(ns geom-voxel-poc.core
  (:require [geom-voxel-poc.generator :as generator]
            [geom-voxel-poc.viewer :as viewer])
  (:gen-class))

(defn -main
  "generate!"
  [& _args]
  (let [file "sphere.stl"]
    (generator/generate! file)
    (viewer/view! file)))
