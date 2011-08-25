## Developing in REPL ##

    ;; Start REPL, then run:
    (require '[appengine-magic.core :as ae])
    (require '[votenoir.app_servlet :as app])
    (ae/serve app/votenoir-app)

    ;; To stop / start
    (ae/stop)
    (ae/serve app/votenoir-app)
    