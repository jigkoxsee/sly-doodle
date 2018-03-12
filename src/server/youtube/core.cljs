(ns youtube.core
  (:require [youtube.impl :as impl]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def ClientSecretSchema
  {:client-id s/Str
   :client-secret s/Str
   :redirect-uris [s/Str]})


(defn authorize [raw-config req callback]
  (let [config (select-keys raw-config [:client-id :client-secret :redirect-uris])]
    (if (s/validate ClientSecretSchema config)
      (impl/authorize config req callback))))


(def UploadRequestParamSchema
  {:filename s/Str
   :title s/Str
   :desc s/Str
   :privacy (s/enum :private :unlisted :public)})

(s/defn ^:always-validate create-upload-request
  [params :- UploadRequestParamSchema]
  """
  Return - (f auth callback) -> nil
    This f will received auth client and callback-fn
  """
  (partial impl/upload-video params))


(def UploadParamSchema
  (merge UploadRequestParamSchema {:client-path s/Str}))


(defn upload-video [{:keys [client-path] :as params} callback]
  (s/validate UploadParamSchema params)
  (authorize
    (impl/read-json-file client-path)
    (create-upload-request
      (dissoc params :client-path))
    callback))



(comment
  (authorize
    (impl/read-json-file "client_secret.json")
    (create-upload-request
        {:filename "tmp-vdo/maprang.mp4" :title "Maprang jumping" :desc "yeahh" :privacy :unlisted})
    #(prn :test-upload-success %))
  (create-upload-request
    {:filename "tmp-vdo/omd.mp4" :title "Maprang jumping" :desc "yeahh" :privacy :unlisted})
  (upload-video
    {:client-path "client_secret.json"
     :filename "tmp-vdo/maprang.mp4" :title "Maprang jumping" :desc "yeahh" :privacy :unlisted}
    #(prn :yo-wow %))
  (impl/read-json-file "client_secret.json")
  (s/validate ClientSecretSchema
              {:client-id  ""
               :client-secret  ""
               :redirect-uris [""]}))

