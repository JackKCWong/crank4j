crank4j
=======

A java port of [Cranker](https://github.com/nicferrier/cranker).

Crank4j consists of 2 executables that together act as a reverse proxy. What it
allows is for cases where there is a firewall between your inside network and
a [DMZ zone](https://en.wikipedia.org/wiki/DMZ_(computing)).

For a normal reverse proxy, a port would need to be open allowing traffic from
the DMZ to the internal network. Crank4j allows you to reverse this: Just open
a port from the internal network to the DMZ, and Crank4j will tunnel HTTP traffic
over the opened network.

So there are two pieces:

* **The router** that faces the internet, and accepts client HTTP requests
* **The connector** that opens connections to the router, and then passes tunneled
requests to target servers.

The connections look like this:

    Browser      |   DMZ        |    Internal Network
     GET   --------> router <-------- connector ---> HTTP Service
                 |              |

But from the point of view of the browser, and your HTTP service, it just looks
like a normal reverse proxy.


Running locally
---------------

### Running from an IDE

1. Open `com.danielflower.crank4j.e2etests.ManualTest` and run the `main` method.
2. Open `https://localhost:9443` in your browser.

### Running against the built uber-jars

1. First, build the package: `mvn package`
2. Start some web service at any location, e.g. `http://localhost:3000` and set this
in `local/connector.properties` under the value `target.uri`
3. Start the router:

        cd local
        start-router.bat or ./start-router.sh
4. In another shell, start the connector:

        cd local
        start-connector.bat or ./start-router.sh
5. Open `https://localhost:8443` in your browser.
