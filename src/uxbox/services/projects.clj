;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.projects
  (:require [suricatta.core :as sc]
            [clj-uuid :as uuid]
            [catacumba.serializers :as sz]
            [buddy.core.codecs :as codecs]
            [uxbox.config :as ucfg]
            [uxbox.schema :as us]
            [uxbox.persistence :as up]
            [uxbox.services.core :as usc]
            [uxbox.services.auth :as usauth]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def +project-schema+
  {:id [us/uuid]
   :user [us/required us/uuid]
   :name [us/required us/string]})

(def +page-schema+
  {:id [us/uuid]
   :user [us/required us/uuid]
   :project [us/required us/uuid]
   :name [us/required us/string]
   :data [us/required us/string]
   :width [us/required us/integer]
   :height [us/required us/integer]
   :layout [us/required us/string]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Repository
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-project
  [conn {:keys [id user name] :as data}]
  {:pre [(us/validate! data +project-schema+)]}
  (let [sql (str "INSERT INTO projects (id, \"user\", name)"
                 " VALUES (?, ?, ?) RETURNING *")
        id (or id (uuid/v4))
        sqlv [sql id user name]]
    (some-> (sc/fetch-one conn sqlv)
            (usc/normalize-attrs))))

(defn create-page
  [conn {:keys [id user project name width height layout data] :as params}]
  {:pre [(us/validate! params +page-schema+)]}
  (let [sql (str "INSERT INTO pages (id, \"user\", project, name, width, "
                 "                   height, layout, data)"
                 " VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING *")
        id (or id (uuid/v4))
        sqlv [sql id user project name width height layout data]]
    (some-> (sc/fetch-one conn sqlv)
            (usc/normalize-attrs))))

(defn update-project
  [conn {:keys [id user name] :as data}]
  {:pre [(us/validate! data +project-schema+)]}
  (let [sql (str "UPDATE projects SET name=?"
                 " WHERE id=? AND \"user\"=? RETURNING *")
        sqlv [sql name id user]]
    (some-> (sc/fetch-one conn sqlv)
            (usc/normalize-attrs))))

(defn update-page
  [conn {:keys [id user project name width height layout data] :as params}]
  {:pre [(us/validate! params +page-schema+)]}
  (let [sql (str "UPDATE pages SET "
                 " name=?, width=?, height=?, layout=?, data=?"
                 " WHERE id=? AND \"user\"=? AND project=?"
                 " RETURNING *")
        sqlv [sql name width height layout data id user project]]
    (some-> (sc/fetch-one conn sqlv)
            (usc/normalize-attrs))))

(defn get-projects-for-user
  [conn user]
  (let [sql (str "SELECT * FROM projects "
                 " WHERE \"user\"=? ORDER BY created_at DESC")]
    (map usc/normalize-attrs
         (sc/fetch conn [sql user]))))

(defn get-pages-for-project-and-user
  [conn user project]
  (let [sql (str "SELECT * FROM pages "
                 " WHERE \"user\"=? AND project=? "
                 " ORDER BY created_at DESC")]
    (->> (sc/fetch conn [sql user project])
         (map usc/normalize-attrs))))

(defn get-project-by-id
  [conn id]
  (let [sqlv ["SELECT * FROM projects WHERE id=?" id]]
    (some-> (sc/fetch-one conn sqlv)
            (usc/normalize-attrs))))

(defn get-page-by-id
  [conn id]
  (let [sqlv ["SELECT * FROM pages WHERE id=?" id]]
    (some-> (sc/fetch-one conn sqlv)
            (usc/normalize-attrs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Service
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- decode-page-data
  [{:keys [data] :as result}]
  (let [data (some-> data
                     (codecs/base64->bytes)
                     (sz/decode :transit+msgpack))]
    (assoc result :data data)))

(defmethod usc/-novelty :project/create
  [conn params]
  (create-project conn params))

(defmethod usc/-novelty :page/create
  [conn {:keys [data] :as params}]
  (let [data (-> (sz/encode data :transit+msgpack)
                 (codecs/bytes->base64))
        params (assoc params :data data)]
    (-> (create-page conn params)
        (decode-page-data))))

(defmethod usc/-novelty :page/update
  [conn {:keys [data] :as params}]
  (let [data (-> (sz/encode data :transit+msgpack)
                 (codecs/bytes->base64))
        params (assoc params :data data)]
    (-> (update-page conn params)
        (decode-page-data))))

(defmethod usc/-query :project/list
  [conn {:keys [user] :as params}]
  (get-projects-for-user conn user))

(defmethod usc/-query :page/list
  [conn {:keys [user project] :as params}]
  (->> (get-pages-for-project-and-user conn user project)
       (map decode-page-data)))