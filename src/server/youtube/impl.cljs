(ns youtube.impl
  (:require
    [camel-snake-kebab.core :refer [->kebab-case ->kebab-case-keyword]]
    [camel-snake-kebab.extras :refer [transform-keys]]
    ["fs" :as fs]
    ["readline" :as readline]
    ["googleapis" :as google]
    ["google-auth-library" :as google-auth]))


;; TODO: Can we import process on require block
(def process js/process)

(def SCOPE ["https://www.googleapis.com/auth/youtube"])
(def TOKEN-DIR "./.credentials/")
(def TOKEN-PATH (str TOKEN-DIR "youtube.json"))
(def SECRET-PATH "./client_secret.json")

(defn read-json-file [filepath]
  (try
    (let [data (-> (fs/readFileSync filepath)
                   (js/JSON.parse)
                   (js->clj :keywordize-keys true)
                   :installed)]
       (transform-keys ->kebab-case-keyword data))
    (catch js/Error  e
      nil)))

(comment
  (read-json-file "client_secret.json"))


(defn read-prev-token []
  (try
    (-> (fs/readFileSync TOKEN-PATH)
        (js/JSON.parse))
    (catch js/Error  e
      nil)))

(defn save-token [token]
  (try
    (fs/mkdirSync TOKEN-DIR)
    (catch js/Error e {}))
  (prn :saved)
  (fs/writeFile TOKEN-PATH (js/JSON.stringify token) #(prn :token-saved)))


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
          (.setCredentials client token)
          (save-token token)
          (cb client))))))



(defn get-new-token [client cb]
  (let [auth-url (.generateAuthUrl
                   client
                   (clj->js {:access_type "offline"
                             :scope SCOPE}))
        rl (readline/createInterface
             (clj->js {:input process.stdin
                       :output process.stdout}))]
    (do
      (prn :visite-here-to-authorize "\n" auth-url)
      (.question rl "Enter Code here: "
                 (fn [code]
                   (prn :code-is code)
                   (.close rl)
                   (get-token client code cb))))))

(defn get-youtube-svc [auth]
  (let [gapi (.-google google)]
    (.youtube
      gapi
      (clj->js {:version "v3"
                :auth auth}))))

(defn create-upload-params [{:keys [filename title desc privacy]}]
  (clj->js
    {:part "id,snippet,status"
     :notifySubscribers false
     :resource
     {:snippet {:title title
                :description desc}
      :status {:privacyStatus (name privacy)}}
     :media
     {:body (fs/createReadStream filename)}}))


(defn upload-video [{:keys [filename] :as params} callback auth]
  (let [svc (get-youtube-svc auth)
        ;; TODO: Handle when file not found
        filesize (aget (fs/statSync filename) "size")]

    ;; TODO: Handle when file not found
    (.videos.insert
      svc
      (create-upload-params params)
      (clj->js
        {:onUploadProgress
         (fn [e]
           (let [bytes-read (aget e "bytesRead")
                 progress (-> bytes-read
                              (* 100)
                              (/ filesize))]
             ;; TODO: Implement progress callback
             (prn :upload-progress progress)))})
      (fn [err data]
        (if err
          (do
            (prn :upload-error err))
          (do
            (prn :upload-success data)
            (callback data)))))))


(defn authorize [{:keys [client-id client-secret redirect-uris]} req callback]
  (let [redirect-uri (first redirect-uris)
        oauth-client (.-OAuth2Client google-auth)
        client (new oauth-client client-id client-secret redirect-uri)]
    (if-let [token (read-prev-token)]
      (do
        (.setCredentials client token)
        (req callback client))
      (get-new-token client (partial req callback)))))
