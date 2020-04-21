(ns moraleofficer.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [clojure.java.browse]
            [clojure.string :as str]
            [reaver :refer [parse extract-from text attr]]))

;; TODO: these are defaults, add some way for users to
;; supply their own subreddits and counts for nefairous purposes
(def cat-subs
  [{:name "CatLoaf" :fetch-count 2}
   {:name "Floof" :fetch-count 2}
   {:name "Blep" :fetch-count 2}
   {:name "CatsStandingUp" :fetch-count 1}
   {:name "CatBellies" :fetch-count 1}
   {:name "DelightfullyChubby" :fetch-count 1}
   {:name "CatsLookingSeductive" :fetch-count 1}
   {:name "CatConspiracy" :fetch-count 1}
   {:name "CatLogic" :fetch-count 1}
   {:name "Meow_Irl" :fetch-count 3}
   {:name "Cats" :fetch-count 2}
   {:name "CatSpotting" :fetch-count 1}
   {:name "CatGifs" :fetch-count 1}
   {:name "KittenGifs" :fetch-count 1}
   {:name "CatVideos" :fetch-count 1}
   {:name "CatsWhoYell" :fetch-count 1}
   {:name "TuckedInKitties" :fetch-count 1}
   {:name "NorwegianForestCats" :fetch-count 1}
   {:name "BabyBigCatGifs" :fetch-count 1}
   {:name "BigCats" :fetch-count 1}])


(defn get-url-content [url]
  ;; Some user string so reddit lets me get sweet data from it
  (let [h {"User-Agent" "Mozilla/5.0 (Windows NT 6.1;) Gecko/20100101 Firefox/13.0.1"}]
    ;; just a little bit of a jitter
    (Thread/sleep (rand-int 2000))
    (client/get url {:headers h
                     :retry-handler (fn [ex try-count http-context]
                                      (if (> try-count 10) false true))})))


(defn get-thumbnails-from-html [resp]
  (extract-from (parse (:body resp)) ".thumbnail"
                [:data-event-action]))


(defn to-full-url [href]
  (if (str/includes? href "http")
    href
    (str "https://old.reddit.com" href)))

(defn trusted-link? [url]
  (cond
    (str/includes? url "giphy") true
    (str/includes? url "imgur") true
    (str/includes? url "redd") true
    :else false))

(defn get-links-for-sub [{:keys [name fetch-count]}]
  (let [url (str "https://old.reddit.com/r/" name "/top")
        resp (get-url-content url)
        thumbnails (get-thumbnails-from-html resp)]
    (->>
     thumbnails
     (map :data-event-action)
     (map :attrs)
     (map :href) ;; filter out the bloody ads
     (filter #(not (str/includes? % "alb.reddit.com")))
     (map to-full-url)
     (filter trusted-link?)
     ;; force eval? I might not need this
     (take fetch-count))))

;; i lost the plot from here on down
(defn delayed-get-link-for-sub [sub]
  ;; a little more jitter
  (Thread/sleep (rand-int 1000))
  (let [links (get-links-for-sub sub)]
    links))

(defn get-the-links []
  (->>
   cat-subs
   (pmap delayed-get-link-for-sub)))

(defn open-links-in-browser [links]
  (map clojure.java.browse/browse-url links))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [links (doall (map open-links-in-browser (get-the-links)))]
    (println "Please wait, generating moral support")
    (run! println links)
    (println "Moral support generated")
    (shutdown-agents)))