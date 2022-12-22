(ns wgctrl.utils.main
	(:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.string :as str])
  (:import [java.io File]
           [java.net URI]
           [java.nio.file Path Files]
           [java.nio.file.attribute FileAttribute PosixFilePermissions]))

(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))
    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e)))))


(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn str->posix
  "Converts a string to a set of PosixFilePermission."
  [s]
  (PosixFilePermissions/fromString s))

(defn- ->posix-file-permissions [s]
  (cond (string? s)
        (str->posix s)
        ;; (set? s)
        ;; (into #{} (map keyword->posix-file-permission) s)
        :else
        s))

(defn- posix->file-attribute [x]
  (PosixFilePermissions/asFileAttribute x))

(defn- posix->attrs
  ^"[Ljava.nio.file.attribute.FileAttribute;" [posix-file-permissions]
  (let [attrs (if posix-file-permissions
                (-> posix-file-permissions
                    (->posix-file-permissions)
                    (posix->file-attribute)
                    vector)
                [])]
    (into-array FileAttribute attrs)))

(defn- as-path
  ^Path [path]
  (if (instance? Path path) path
      (if (instance? URI path)
        (java.nio.file.Paths/get ^URI path)
        (.toPath (io/file path)))))


(defn create-temp-file
  "Creates an empty temporary file using Files#createTempFile.
  - `(create-temp-file)`: creates temp file with random prefix and suffix.
  - `(create-temp-dir {:keys [:prefix :suffix :path :posix-file-permissions]})`: create
  temp file in path with prefix. If prefix and suffix are not
  provided, random ones are generated. The `:posix-file-permissions`
  option is a string like `\"rwx------\"`."
  ([]
   (Files/createTempFile
    (str (java.util.UUID/randomUUID))
    (str (java.util.UUID/randomUUID))
    (make-array FileAttribute 0)))
  ([{:keys [:path :prefix :suffix :posix-file-permissions]}]
   (let [attrs (posix->attrs posix-file-permissions)
         prefix (or prefix (str (java.util.UUID/randomUUID)))
         suffix (or suffix (str (java.util.UUID/randomUUID)))]
     (if path
       (Files/createTempFile
        (as-path path)
        prefix
        suffix
        attrs)
       (Files/createTempFile
        prefix
        suffix
        attrs)))))

