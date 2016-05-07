;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.util.data
  "Data transformations utils."
  (:require [clojure.walk :as walk]
            [cuerdas.core :as str]))

(defn normalize-attrs
  "Recursively transforms all map keys from strings to keywords."
  [m]
  (letfn [(tf [[k v]]
            (let [ks (-> (name k)
                         (str/replace "_" "-"))]
              [(keyword ks) v]))
          (walker [x]
            (if (map? x)
              (into {} (map tf) x)
              x))]
    (walk/postwalk walker m)))
