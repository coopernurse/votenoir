## Developing in REPL ##

    ;; Start REPL, then run:
    (require '[appengine-magic.core :as ae])
    (require '[votenoir.app_servlet :as app])
    (ae/serve app/votenoir-app)

    ;; To stop / start
    (ae/stop)
    (ae/serve app/votenoir-app)

## Pushing to App Engine ##

    lein appengine-prepare
    ~/bin/appengine-java-sdk-1.5.3/bin/appcfg.sh update war