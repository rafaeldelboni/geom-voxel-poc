(ns geom-voxel-poc.viewer
  (:require [clojure.java.io :as io]
            [thi.ng.color.core :as col]
            [thi.ng.dstruct.core :as d]
            [thi.ng.geom.aabb :as a]
            [thi.ng.geom.core :as g]
            [thi.ng.geom.gl.arcball :as arc]
            [thi.ng.geom.gl.core :as gl]
            [thi.ng.geom.gl.fx :as fx]
            [thi.ng.geom.gl.fx.bloom :as bloom]
            [thi.ng.geom.gl.glmesh :as glm]
            [thi.ng.geom.gl.jogl.constants :as jlc]
            [thi.ng.geom.gl.jogl.core :as jogl]
            [thi.ng.geom.gl.shaders :as sh]
            [thi.ng.geom.gl.shaders.phong :as phong]
            [thi.ng.geom.matrix :as mat]
            [thi.ng.geom.mesh.io :as mio]
            [thi.ng.geom.quaternion :as q]
            [thi.ng.geom.rect :as r]
            [thi.ng.geom.utils :as gu]
            [thi.ng.geom.vector :as v]
            [thi.ng.math.core :as m])
  (:import [com.jogamp.newt.event KeyEvent MouseEvent]
           [com.jogamp.opengl GL3 GLAutoDrawable]))

(defonce app (atom {:version 330}))

(defn load-mesh
  "Loads STL mesh from given path and fits it into centered bounding box."
  [path bounds]
  (with-open [in (io/input-stream path)]
    (->> #(glm/gl-mesh % #{:fnorm})
         (mio/read-stl (mio/wrapped-input-stream in))
         vector
         (gu/fit-all-into-bounds (g/center bounds))
         first)))

(defn mesh->model [mesh gl shader]
  (-> (load-mesh mesh (a/aabb 2))
      (gl/as-gl-buffer-spec {})
      (d/merge-deep
       {:uniforms {:view        (mat/look-at (v/vec3 0 0 1) (v/vec3) v/V3Y)
                   :lightPos    [0 2 0]
                   :shininess   10
                   :wrap        0
                   :ambientCol  [0.0 0.1 0.4 0.0]
                   :diffuseCol  [0.1 0.6 0.8]
                   :specularCol [1 1 1]}
        :shader shader})
      (gl/make-buffers-in-spec gl jlc/dynamic-draw)))

(defn init
  [^GLAutoDrawable drawable]
  (let [{:keys [mesh version]} @app
        ^GL3 gl       (.. drawable getGL getGL3)
        _view-rect    (gl/get-viewport-rect gl)
        main-shader   (sh/make-shader-from-spec gl phong/shader-spec version)
        pass-shader   (sh/make-shader-from-spec gl fx/shader-spec version)
        fx-pipe       (fx/init-pipeline gl (bloom/make-pipeline-spec 1280 720 16 version))
        quad          (fx/init-fx-quad gl)
        img-comp      (d/merge-deep
                       quad
                       {:shader (assoc-in (get-in fx-pipe [:shaders :final]) [:state :tex]
                                          (fx/resolve-pipeline-textures fx-pipe [:src :ping]))})
        img-orig      (d/merge-deep
                       quad
                       {:shader (assoc-in pass-shader [:state :tex]
                                          (fx/resolve-pipeline-textures fx-pipe :src))})
        model         (mesh->model mesh gl main-shader)]
    (swap! app merge
           {:drawable    drawable
            :gl          gl
            :main-shader main-shader
            :model       model
            :fx-pipe     fx-pipe
            :img-comp    img-comp
            :thumbs      (-> fx-pipe :passes butlast reverse vec (conj img-orig))
            :arcball     (arc/arcball {:init (m/normalize (q/quat 0.0 0.707 0.707 0))})})))

(defn display
  [^GLAutoDrawable drawable _t]
  (let [^GL3 gl (.. drawable getGL getGL3)
        {:keys [model arcball fx-pipe thumbs width height]} @app
        src-fbo   (get-in fx-pipe [:fbos :src])
        view-rect (r/rect width height)
        vp        (-> view-rect
                      (gu/fit-all-into-bounds [(r/rect (:width src-fbo) (:height src-fbo))])
                      first
                      (g/center (g/centroid view-rect)))
        fx-pipe   (fx/update-pipeline-pass fx-pipe :final assoc :viewport vp)]
    (gl/bind (:fbo src-fbo))
    (doto gl
      (gl/set-viewport 0 0 (:width src-fbo) (:height src-fbo))
      (gl/clear-color-and-depth-buffer (col/hsva 0 0 0.3) 1)
      (gl/draw-with-shader (assoc-in model [:uniforms :model] (arc/get-view arcball))))
    (gl/unbind (:fbo src-fbo))
    (fx/execute-pipeline gl fx-pipe)
    (loop [y 0, thumbs thumbs]
      (when thumbs
        (gl/set-viewport gl 0 y 160 90)
        (gl/draw-with-shader gl (first thumbs))
        (recur (+ y 90) (next thumbs))))))

(defn key-pressed
  [^KeyEvent e]
  (condp = (.getKeyCode e)
    KeyEvent/VK_ESCAPE (jogl/destroy-window (:window @app))
    nil))

(defn resize
  [_ _x _y w h]
  (swap! app
         #(-> %
              (assoc-in [:model :uniforms :proj] (mat/perspective 45 (/ w h) 0.1 10))
              (assoc :width w :height h)
              (update :arcball arc/resize w h))))

(defn dispose [_]
  (jogl/stop-animator (:anim @app)))

(defn mouse-pressed [^MouseEvent e]
  (swap! app update :arcball arc/down (.getX e) (.getY e)))

(defn mouse-dragged [^MouseEvent e]
  (swap! app update :arcball arc/drag (.getX e) (.getY e)))

(defn wheel-moved [^MouseEvent _e deltas]
  (swap! app update :arcball arc/zoom-delta (nth deltas 1)))

(defn view! [mesh]
  (when (:window @app)
    (jogl/destroy-window (:window @app)))
  (swap! app assoc :mesh mesh)
  (swap! app d/merge-deep
         (jogl/gl-window
          {:profile       :gl3
           :samples       4
           :double-buffer true
           :fullscreen    false
           :events        {:init    init
                           :display display
                           :dispose dispose
                           :resize  resize
                           :keys    {:press key-pressed}
                           :mouse   {:press mouse-pressed
                                     :drag  mouse-dragged
                                     :wheel wheel-moved}}})))
