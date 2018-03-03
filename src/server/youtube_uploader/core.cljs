(ns youtube-uploader.core
  (:require
    [youtube-uploader.config :refer [env]]
    [youtube-uploader.middleware :refer [wrap-defaults]]
    [youtube-uploader.routes :refer [router]]
    [macchiato.server :as http]
    [macchiato.middleware.session.memory :as mem]
    [mount.core :as mount :refer [defstate]]
    [taoensso.timbre :refer-macros [log trace debug info warn error fatal]]
    ["fs" :as fs]
    ["readline" :as readline]
    ["googleapis" :as google]
    ["google-auth-library" :as google-auth]))


;; TODO: Can we import process on require block
(def process js/process)

(def SCOPE ["https://www.googleapis.com/auth/youtube.readonly"])
(def TOKEN-DIR "./.credentials/")
(def TOKEN-PATH (str TOKEN-DIR "youtube.json"))
(def SECRET-PATH)

(defn read-json-file [filepath]
  (try
    (-> fs
        (.readFileSync filepath)
        (js/JSON.parse))
    (catch js/Error  e
      nil)))

(defn yt []
  ;; Try catch
  (def cc (read-json-file "client_secret.json")))

;; TODO :: for dev
(yt)

(defn read-prev-token []
  (read-json-file TOKEN-PATH))

(defn save-token [token]
  (try
    (.mkdirSync fs TOKEN-DIR)
    (catch js/Error e {}))
  (prn :saved)
  (.writeFile fs TOKEN-PATH (js/JSON.stringify token)))


(defn get-token [client code cb]
  (prn :get-token code)
  (.getToken
    client
    code
    (fn [err token]
      (if err
        (prn :error err))
      (if token
        (do
          (set! client.-credentials token)
          (save-token token)
          (cb))))))



(defn get-new-token [client cb]
  (let [auth-url (.generateAuthUrl
                   client
                   (clj->js {:access_type "offline"
                             :scope SCOPE}))
        rl (.createInterface
             readline
             (clj->js {:input process.stdin
                       :output process.stdout}))]
    (do
      (prn :visite-here "\n" auth-url)
      (.question rl "Enter Code here: "
                 (fn [code]
                   (prn :code-is code)
                   (.close rl)
                   (get-token client code cb))))))

(defn get-channel [auth]
  (let [gapi (.-google google)
        svc (.youtube gapi (clj->js {:version "v3"
                                     :auth auth}))]
    (def yc auth)
    (def ys svc)
    (svc.channels.list
      (clj->js {:part "snippet,contentDetails,statistics"
                :forUsername "GoogleDevelopers"})
      (fn [err res]
        (if err
          (prn :get-chan-err err)
          (prn :get-chan-succ res))))))


(defn authorize [cred cb]
  (let [client-secret (aget cred "installed" "client_secret")
        client-id (aget cred "installed" "client_id")
        redirect-url (aget cred "installed" "redirect_uris" 0)
        oauth-client (.-OAuth2Client google-auth)
        client (new oauth-client client-id client-secret redirect-url)]
    (if-let [token (read-prev-token)]
      (do
        (.setCredentials client token)
        (cb client)
        client)
      (get-new-token client #(prn :get-token-done %)))))

(defn server []
  (mount/start)
  (let [host (or (:host @env) "127.0.0.1")
        port (or (some-> @env :port js/parseInt) 3000)]
    (http/start
      {:handler    (wrap-defaults router)
       :host       host
       :port       port
       :on-success #(info "youtube-uploader started on" host ":" port)})))
