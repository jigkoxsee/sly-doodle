(ns youtube.core
  (:require
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
    (-> (fs/readFileSync filepath)
        (js/JSON.parse))
    (catch js/Error  e
      nil)))

(comment
  (read-json-file "client_secret.json"))


(defn read-prev-token []
  (read-json-file TOKEN-PATH))

(defn save-token [token]
  (try
    (fs/mkdirSync TOKEN-DIR)
    (catch js/Error e {}))
  (prn :saved)
  (fs/writeFile TOKEN-PATH (js/JSON.stringify token)))


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

(defn get-youtube-svc [auth]
  (let [gapi (.-google google)]
    (.youtube
      gapi
      (clj->js {:version "v3"
                :auth auth}))))


(defn get-channel [auth]
  (let [svc (get-youtube-svc auth)]
    (def yc auth)
    (def ys svc)
    (svc.channels.list
      (clj->js {:part "snippet,contentDetails,statistics"
                :forUsername "GoogleDevelopers"})
      (fn [err res]
        (if err
          (prn :get-chan-err err)
          (prn :get-chan-succ res))))))


(defn upload-video [{:keys [filename title desc]} auth]
  (let [svc (get-youtube-svc auth)
        privacy "unlisted"
        ;privacy "private"
        ;; TODO: Handle when file not found
        filesize (aget (fs/statSync filename) "size")]
    (prn :upload-video)
    ;; TODO: Handle when file not found
    (svc.videos.insert
      (clj->js
        {:part "id,snippet,status"
         :notifySubscribers false
         :resource
         {:snippet {:title title
                    :description desc}
          :status {:privacyStatus privacy}}
         :media
         {:body (fs/createReadStream filename)}})
      (clj->js
        {:onUploadProgress
         (fn [evt]
           (let [bytes-read (aget evt "bytesRead")
                 progress (-> bytes-read
                              (* 100)
                              (/ filesize))]
             (prn :upload-progress progress)))})
      (fn [err data]
        (if err
          (do
            (def e err)
            (prn :upload-error err))

          (do
            (prn :upload-success data)))))))


(defn authorize [cred cb]
  (let [client-secret (aget cred "installed" "client_secret")
        client-id (aget cred "installed" "client_id")
        redirect-url (aget cred "installed" "redirect_uris" 0)
        oauth-client (.-OAuth2Client google-auth)
        client (new oauth-client client-id client-secret redirect-url)]
    (if-let [token (read-prev-token)]
      (do
        (.setCredentials client token)
        (cb client))
      (get-new-token client cb))))
