package server

import datadog.trace.agent.test.base.HttpServer
import datadog.trace.agent.test.base.HttpServerTest
import ratpack.exec.Promise
import ratpack.groovy.test.embed.GroovyEmbeddedApp

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.FORWARDED
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_ENCODED_BOTH
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_ENCODED_QUERY
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class RatpackAsyncHttpServerTest extends RatpackHttpServerTest {

  @Override
  HttpServer server() {
    return new RatpackServer(GroovyEmbeddedApp.ratpack {
      serverConfig {
        port 0
        address InetAddress.getByName('localhost')
      }
      bindings {
        bind TestErrorHandler
      }
      handlers {
        prefix(SUCCESS.relativeRawPath()) {
          all {
            Promise.sync {
              SUCCESS
            } then { HttpServerTest.ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        prefix(FORWARDED.relativeRawPath()) {
          all {
            Promise.sync {
              FORWARDED
            } then { HttpServerTest.ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(request.headers.get("x-forwarded-for"))
              }
            }
          }
        }
        prefix(QUERY_ENCODED_BOTH.relativeRawPath()) {
          all {
            Promise.sync {
              QUERY_ENCODED_BOTH
            } then { HttpServerTest.ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.bodyForQuery(request.query))
              }
            }
          }
        }
        prefix(QUERY_ENCODED_QUERY.relativeRawPath()) {
          all {
            Promise.sync {
              QUERY_ENCODED_QUERY
            } then { HttpServerTest.ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.bodyForQuery(request.query))
              }
            }
          }
        }
        prefix(QUERY_PARAM.relativeRawPath()) {
          all {
            Promise.sync {
              QUERY_PARAM
            } then { HttpServerTest.ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.bodyForQuery(request.query))
              }
            }
          }
        }
        prefix(REDIRECT.relativeRawPath()) {
          all {
            Promise.sync {
              REDIRECT
            } then { HttpServerTest.ServerEndpoint endpoint ->
              controller(endpoint) {
                context.redirect(endpoint.body)
              }
            }
          }
        }
        prefix(ERROR.relativeRawPath()) {
          all {
            Promise.sync {
              ERROR
            } then { HttpServerTest.ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        prefix(EXCEPTION.relativeRawPath()) {
          all {
            Promise.sync {
              EXCEPTION
            } then { HttpServerTest.ServerEndpoint endpoint ->
              controller(endpoint) {
                throw new Exception(endpoint.body)
              }
            }
          }
        }
      }
    })
  }
}
