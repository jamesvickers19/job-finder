(ns job-finder.core
  (:require [clojure.string :as string])
  (:import (java.net URLEncoder)
           (java.nio.charset StandardCharsets)))

(defn get-page
  "Gets a page"
  [address]
  (when address
    (println "get page: " address)
    (try
      (-> (org.jsoup.Jsoup/connect address)
          (.userAgent "ExampleBot 1.0 (+http://botexample.com/)")
          (.followRedirects true)
          .get)
      (catch Exception e
        (println "Link" address "failed:" (.getMessage e))
        nil))))

(defn get-link
  "Gets the 'href' of a HTML element"
  [^org.jsoup.nodes.Element item]
  (.attr item "href"))

(defn with-dot-com
  [s]
  (str s ".com"))

(defn with-https
  [domain]
  (str "https://" domain))

(defn remove-spaces
  [s]
  (.replace s " " ""))

(defn- old-new-company-names
  [company-name-with-acquired-by]
  (let [[old new] (.split company-name-with-acquired-by "\\(acquired by")]
    [(with-dot-com old)
     (-> new (.split "\\)") first with-dot-com)]))

(defn company-desc->urls
  [company-desc]
  (when company-desc
    (let [company-name (.toLowerCase company-desc)
          domains (cond
                    ; if name has a dot, just use the name
                    (.contains company-name ".") [company-name]
                    ; if it says 'acquired by', try old and new company name
                    (.contains company-name "(acquired by") (old-new-company-names company-name)
                    ; if name has a parenthesis, take everything before it
                    (.contains company-name "(") [(-> company-name
                                                      (.split "\\(")
                                                      first
                                                      with-dot-com)]
                    ; if name has slash, try names before and after it
                    (.contains company-name "/") (mapv with-dot-com (.split company-name "/"))
                    :else [(with-dot-com company-name)])]
      (map (comp with-https remove-spaces) domains))))

(comment
  (map company-desc->urls ["LonoCloud (acquired by ViaSat)"
                           "8th Light"
                           "Iris.tv"
                           "Marktbauer/Comida da gente"]))

(defn url-encode
  [s]
  (URLEncoder/encode s StandardCharsets/UTF_8))

(defn find-company-url-via-search
  "Tries to find company homepage by opening the first search result for them"
  [company-desc]
  (let [results-page (->> (str company-desc " careers")
                          url-encode
                          (str "https://www.google.com/search?btnI=I'm+Feeling+Lucky&q=")
                          get-page)
        company-url (some-> results-page
                            (.select "a")
                            first
                            get-link)]
    (when (and company-url (.contains company-url "http"))
      company-url)))

(defn find-company-urls
  [company-desc]
  (or (->> company-desc
           company-desc->urls
           (filter get-page)
           not-empty)
      [(find-company-url-via-search company-desc)]))

(defn get-clojure-company-list
  "Extracts the URL's of the companies using Clojure from the community page"
  []
  (let [page (get-page "https://clojure.org/community/companies")
        company-list-items (.getElementsByTag page "li")]
    (->> company-list-items
         (map #(.text %))
         (map find-company-urls)
         flatten
         (filter some?))))

(defn contains-any-substring?
  "Returns true if s contains a string in substrings, false otherwise."
  [s substrings]
  (some true? (map #(.contains (.toLowerCase s) (.toLowerCase %)) substrings)))

(defn make-absolute
  "Makes a full URL given a base-url and endpoint.  endpoint may be a full URL
  itself; if it doesn't start with / it's assumed to be as such and is just returned."
  [base-url endpoint]
  (if (.startsWith endpoint "/")
    (str (.replaceAll base-url "/$" "") endpoint)
    endpoint))

(defn find-job-link
  "Finds the 'jobs' link given a page and it's URL.
  Returns nil if one can't be found, commonly because the page source uses
  Javascript to generate HTML (this program only deals in straight HTML)"
  [page url]
  (let [search-terms #{"jobs" "career" "work for us" "work with us" "join us" "openings"}
        search-term-matches #(contains-any-substring? % search-terms)
        links (.getElementsByTag page "a")
        is-jobs? #(or (search-term-matches (.toLowerCase (get-link %)))
                      (search-term-matches (.toLowerCase (.text %))))
        job-url (first (filter is-jobs? links))]
    (if job-url
      (make-absolute url (get-link job-url))
      nil)))


(defn search-jobs
  "Given company-urls, accesses the site and tries to find the 'jobs' page
  and whether it has remote jobs.  Returns a map with keys
  [:no-company-page :no-job-page :job-page :job-page-remote]
  to vectors that contain URL's - either elements from company-urls or discovered job pages."
  [company-urls]
  (reduce
   (fn [m url]
     (if-let [page (get-page url)]
       (let [job-link (find-job-link page url)]
         (if-let [job-link-page (get-page job-link)]
           (let [remote? (contains-any-substring? (.. job-link-page text toLowerCase)
                                                  #{"remote" "work from home" "anywhere" "any location"})
                 k (if remote? :job-page-remote :job-page)]
             (update m k #(conj % job-link)))
           (update m :no-job-page #(conj % url))))
       (update m :no-company-page #(conj % url))))
   {:no-company-page []
    :no-job-page []
    :job-page []
    :job-page-remote []}
   company-urls))

(defn write-map-to-files!
  "Takes a map presumed to have collection values
  and writes a file named as each key with one line
  per element in the value collection."
  [m]
  (doall
   (map (fn [[k v]] (spit (str (name k) ".txt")
                          (string/join "\n" v)))
        m)))

; Example usage:
(comment
  (->> (get-clojure-company-list)
       search-jobs
       write-map-to-files!))