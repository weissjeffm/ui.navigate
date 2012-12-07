(ns ui.navigate
  (:require [clojure.zip :as zip]
            [clojure.data.zip :as zf]
            [clojure.set])
  (:import [java.util NoSuchElementException]))

(defmacro nav-tree "takes a nested vector structure and returns nested maps"
  [[page-id args form & links]]
  (merge `{:page ~page-id
           :fn (fn ~args ~form)
           :req-args ~(vec (map keyword args))}
         (if links
           {:links (vec (for [link links]
                          `(nav-tree ~link)))}
           {})))

(defn page-zip [tree] (zip/zipper (constantly true)
                                  #(:links %)
                                  #(conj %1 {:links %2})
                                  tree))

(defn matches-page
  "Returns predicate for a loc to match a page named page."
  [page]
  (fn [loc]
    (= (-> loc zip/node :page)
       page)))

(defn find-node "Finds first node in z that matches pred."
  [z pred]
  (and z (->> (iterate zip/next z)
            (take-while #(not (zip/end? %1)))
            (some  #(when (pred %) %)))))

(defn page-path
  "Returns a list, the path from the root of zipper tree z to page."
  [page z]
  (let [first-match (or (find-node z (matches-page page))
                        (throw (NoSuchElementException.
                                (str "Page " page " was not found in navigation tree."))))]
    (conj (zip/path first-match) (zip/node first-match))))

(defn navigate 
  ([page z args]
     (let [path (page-path page z)
           all-req-args (set (mapcat :req-args path))
           missing-args (clojure.set/difference all-req-args (set (keys args)))]
       (if-not (zero? (count missing-args))
         (throw (IllegalArgumentException. (str "Missing required keys to navigate to " page " - " missing-args)))
         (doseq [step path]
           (apply (:fn step)
                  (for [req-arg (:req-args step)]
                    (req-arg args)))))))
  ([page z] (navigate page z {})))

(defn nav-fn
  "Closes over a page zip structure and returns a navigation function.
  nav-tree-ref should be an IDeref (var, atom, etc) containing a
  page-zip zipper structure. deref is used here so you can update the
  page structure during development, without having to call this
  function again."
  [nav-tree-ref]
  (fn 
    ([page args]
       (navigate page (page-zip (deref nav-tree-ref)) args))
    ([page] (navigate page (page-zip (deref nav-tree-ref)) {}))))

(defn add-subnav
  "In nav-tree tree, add subnavigation branch as a child of parent
  page. If branch is already present, replaces that branch."
  [tree parent-page branch]
  (let [parent-node (find-node (page-zip tree) (matches-page parent-page))]
    (assert parent-node (format "Graft point %s not found in tree." parent-page))
    (if-let [existing-child-loc (->> parent-node
                                   zf/children
                                   (filter (matches-page (:page branch)))
                                   first)]
      (-> existing-child-loc (zip/replace branch) zip/root)
      (-> parent-node (zip/append-child branch) zip/root))))