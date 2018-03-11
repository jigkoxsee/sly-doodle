(ns youtube-uploader.core
  (:require
    [youtube-uploader.config :refer [env]]
    [youtube-uploader.middleware :refer [wrap-defaults]]
    [youtube-uploader.routes :refer [router]]
    [macchiato.server :as http]
    [macchiato.middleware.session.memory :as mem]
    [mount.core :as mount :refer [defstate]]
    [youtube.core :as yt]
    [taoensso.timbre :refer-macros [log trace debug info warn error fatal]]))

(comment
  (let [client-secret (yt/read-json-file yt/SECRET-PATH)]
    (yt/authorize client-secret (partial yt/upload-video {:title "AIS D.C"
                                                          :desc "Working together"
                                                          :filename "./tmp-vdo/dc.mp4"}))))



(defn server []
  (mount/start)
  (let [host (or (:host @env) "127.0.0.1")
        port (or (some-> @env :port js/parseInt) 3000)]
    (http/start
      {:handler    (wrap-defaults router)
       :host       host
       :port       port
       :on-success #(info "youtube-uploader started on" host ":" port)})))
