(ns job-finder.core)
  ;(:import [org.jsoup Jsoup]))


(defn get-page
  [address]
  (if (nil? address)
    nil
    (do
      (println "get page: " address)
      (try
        (.get (org.jsoup.Jsoup/connect address))
        (catch Exception e
          (println "Link" address "failed:" (.getMessage e))
          nil)))))

(defn get-link [^org.jsoup.nodes.Element item]
  (.attr item "href"))

(defn get-company-link
  [^org.jsoup.nodes.Element item]
  (let [item_p (.get (.getElementsByTag item "p") 0)
        a (.get (.getElementsByTag item_p "a") 0)]
    (get-link a)))

(defn get-company-list
  []
  (let [page (get-page "https://clojure.org/community/companies")
        list-items (.getElementsByTag page "li")]
    (map get-company-link list-items)))

(defn contains-any-substring?
  [s substrings]
  (some true? (map #(.contains (.toLowerCase s) (.toLowerCase %)) substrings)))

(defn make-absolute
  [base-url endpoint]
  (if (.startsWith endpoint "/")
    (str (.replaceAll base-url "/$" "") endpoint)
    endpoint))

(defn find-job-link
  [page url]
  (let [search-terms #{"jobs" "career" "work for us" "work with us" "join us"}
        search-term-matches #(contains-any-substring? % search-terms)
        links (.getElementsByTag page "a")
        is-jobs? #(or (search-term-matches (.toLowerCase (get-link %)))
                      (search-term-matches (.toLowerCase (.text %))))
        job-url (first (filter is-jobs? links))]
    (if job-url
      (make-absolute url (get-link job-url))
      nil)))


(defn search-jobs
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

; Call search-jobs and write each key into a text file

; figure out how to open browser with list of pages (like in a text file)