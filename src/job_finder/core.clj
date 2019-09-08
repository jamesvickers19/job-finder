(ns job-finder.core)

(defn get-page
  "Gets a page"
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
  "Gets the 'href' of a HTML element"
  (.attr item "href"))

(defn get-clojure-company-link
  "Extracts a clojure company link, drilling down into the HTML to get it"
  [^org.jsoup.nodes.Element item]
  (let [item_p (.get (.getElementsByTag item "p") 0)
        a (.get (.getElementsByTag item_p "a") 0)]
    (get-link a)))

(defn get-clojure-company-list
  "Extracts the URL's of the companies using Clojure from the community page"
  []
  (let [page (get-page "https://clojure.org/community/companies")
        list-items (.getElementsByTag page "li")]
    (map get-clojure-company-link list-items)))

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
                           (clojure.string/join "\n" v)))
         m)))

; Example usage:
(comment
  (write-map-to-files! (search-jobs (get-clojure-company-list))))
