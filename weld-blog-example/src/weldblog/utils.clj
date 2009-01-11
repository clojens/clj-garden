(ns weldblog.utils
  (:use clojure.contrib.prxml (clj-html core utils)))

(defmacro xml
  [& body]
  `(with-out-str (prxml ~@body)))

(defmacro defxml
  [name args & body]
  `(defn ~name ~args (xml ~@body)))

(defmacro for-html
  [[elem-bind coll] & body]
  `(domap-str [~elem-bind ~coll]
     (html ~@body)))
