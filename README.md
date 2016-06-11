crank4j
=======

A java port of [Cranker](https://github.com/nicferrier/cranker).

Todo
----

* Make sure it behaves as a reverse proxy - [follow this?](https://www.mnot.net/blog/2011/07/11/what_proxies_must_do)
  - done: sections 1, 2, 4, maybe 6, maybe 7, 8, 9, 10 (irrelevant), 11 (irrelevant)
* Skip request streaming when no request body. From https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-14#section-3.3

            The presence of a message-body in a request is signaled by the
              inclusion of a Content-Length or Transfer-Encoding header field in
              the request's header fields, even if the request method does not
              define any use for a message-body.  This allows the request message
              framing algorithm to be independent of method semantics.
