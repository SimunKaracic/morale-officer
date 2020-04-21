(ns learning.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [clojure.java.browse]
            [clojure.string :as str]
            [reaver :refer [parse extract-from text attr]]))

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
   {:name "BigCats" :fetch-count 1}
   {:name "wdwakjdwalkj" :fetch-count 1}])

(defn generate-links-from-subs [subs]
  (->>
   subs
   (map :name)
   (map #(str "https://reddit.com/r/" %))))

;; (generate-links-from-subs cat-subs)

;; (defn open-subs-in-browser [subs]
;;   (let [links (generate-links-from-subs subs)]
;;     (map clojure.java.browse/browse-url links)))

(defn get-url-content [url]
  (let [h {"User-Agent" "Mozilla/5.0 (Windows NT 6.1;) Gecko/20100101 Firefox/13.0.1"}]
    (Thread/sleep (rand-int 5000))
    (client/get url {:headers h
                     :retry-handler (fn [ex try-count http-context]
                                      (if (> try-count 10) false true))})))

(defn get-thumbnails-from-html [resp]
  (extract-from (parse (:body resp)) ".thumbnail"
                [:data-event-action]))


(defn get-absolute-url [url]
  (if (str/includes? url "http")
    url
    (str "https://old.reddit.com" url)))

(defn get-links-for-sub [{:keys [name fetch-count]}]
  ;; generate the url from name
  ;; get the content
  ;; get the thumbnails
  (let [url (str "https://old.reddit.com/r/" name "/top")
        resp (get-url-content url)
        thumbnails (get-thumbnails-from-html resp)]
    (->>
     thumbnails
     (map :data-event-action)
     (map :attrs)
     (map :href) ;; filter out the bloody ads
     (filter #(not (str/includes? % "alb.reddit.com")))
     (map get-absolute-url)
     (take fetch-count))))

(defn delayed-get-link-for-sub [sub]
  (Thread/sleep (rand-int 1000))
  (let [links (get-links-for-sub sub)]
    links))
    ;; (map println links)))
;; (map clojure.java.browse/browse-url
;;  (delayed-get-link-for-sub {:name "aww" :fetch-count 3}))
;; (def links (get-links-for-sub (first cat-subs)))
;; (first links)
;; (map get-links-for-sub cat-subs)
(defn get-the-links []
  (->>
   cat-subs
   (pmap delayed-get-link-for-sub)))

(defn open-links-in-browser [links]
  (map clojure.java.browse/browse-url links))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [what-is-it (doall (map open-links-in-browser (get-the-links)))]
    (println "Please wait, generating moral support")
    (run! println what-is-it)
    (println "Moral support generated")
    (shutdown-agents)))