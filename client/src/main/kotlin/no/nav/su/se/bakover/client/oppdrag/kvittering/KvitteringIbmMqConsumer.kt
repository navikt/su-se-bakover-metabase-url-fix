package no.nav.su.se.bakover.client.oppdrag.kvittering

import org.slf4j.LoggerFactory
import javax.jms.Connection
import javax.jms.Session

class KvitteringIbmMqConsumer(
    kvitteringQueueName: String,
    connection: Connection
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val secureLog = LoggerFactory.getLogger("sikkerlogg")
    private val jmsSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE) // TODO vurder hvordan vi skal håndtere dette
    private val consumer = jmsSession.createConsumer(jmsSession.createQueue(kvitteringQueueName))

    init {
        consumer.setMessageListener { message ->
            try {
                val body = message.getBody(String::class.java)
                try {
                    secureLog.info("Mottok kvittering fra oppdrag body: $body")
                    onMessage(body)
                } catch (err: Exception) {
                    log.error("Feil med mottak av MQ-melding: ${err.message}", err)
                    secureLog.error("Feil med mottak av MQ-melding: ${err.message} $body", err)
                }
            } catch (err: Exception) {
                log.error("Klarte ikke å hente ut meldingsinnholdet: ${err.message}", err)
            }
        }
    }

    private fun onMessage(xmlMessage: String) {
        log.info("Received message: $xmlMessage")
    }
}
