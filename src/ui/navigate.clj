(ns ui.navigate
  (:require [clojure.zip :as zip]
            [clojure.data.zip :as zf]
            [clojure.set])
  (:import [java.util NoSuchElementException]))

(defn as-tree "takes a nested vector structure and returns nested maps"
  [[page-id step-fn & links]]
  (merge `{:page ~page-id
           :fn ~step-fn}
         (if links
           {:links (vec (for [link links]
                          `(nav-tree ~link)))}
           {})))

(defmacro nav-tree "Formats literal nested vector as a page tree."
  [args]
  (as-tree args))

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
  "Returns a list, the path from the start-page of zipper tree, to
   end-page. start-page defaults to the root of the tree."
  ([start-page end-page z]
     (let [first-match (or (find-node z (matches-page end-page))
                           (throw (NoSuchElementException.
                                   (str "Page " end-page " was not found in navigation tree."))))]
       (drop-while #(not= (:page %) start-page)
                   (conj (zip/path first-match) (zip/node first-match)))))
  ([end-page z]
     (page-path (-> z zip/root :page) end-page z)))

(defn navigate 
  [start-page end-page z args]
  (doseq [step  end-page z]
    (apply (:fn step) args)))

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
    ([page] (navigate page (page-zip (deref nav-tree-ref)) (list)))))

(defn add-subnav-multiple
  "Add multiple branches to the same parent."
  [tree [parent-page branches]]
  (let [parent-node (find-node (page-zip tree) (matches-page parent-page))]
    (assert parent-node (format "Graft point %s not found in tree." parent-page))
    (loop [z parent-node branches branches]
      (if-let [branch (first branches)]
        (if-let [existing-child-loc (->> z
                                         zf/children  ;; because we wants the locs, not the nodes
                                         (filter (matches-page (:page branch)))
                                         first)]
          (recur (-> existing-child-loc (zip/replace branch) zip/up) (rest branches))
          (recur (zip/append-child z branch) (rest branches)))
        (zip/root z)))))

(defn add-subnav
  "In nav-tree tree, add subnavigation branch as a child of parent
  page. If branch is already present, replaces that branch."
  [tree [parent-page branch]]
  (add-subnav-multiple tree parent-page (list branch)))
