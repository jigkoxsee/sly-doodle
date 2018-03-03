(ns youtube-uploader.app
  (:require
    [doo.runner :refer-macros [doo-tests]]
    [youtube-uploader.core-test]))

(doo-tests 'youtube-uploader.core-test)


