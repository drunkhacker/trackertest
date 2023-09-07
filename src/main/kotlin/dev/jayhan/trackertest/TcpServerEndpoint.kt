package dev.jayhan.trackertest

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.Transformers
import org.springframework.integration.endpoint.MessageProducerSupport
import org.springframework.integration.ip.IpHeaders
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.GenericMessage
import org.springframework.stereotype.Component


@Component
class TcpServerEndpoint(
    private val tcpChannelAdapter: MessageProducerSupport,
    private val tcpOutboundChannel: MessageChannel,
    private val tcpSendingMessageHandler: MessageHandler
) {
    @Bean
    fun serverFlow(): IntegrationFlow {
        return IntegrationFlow.from(tcpChannelAdapter)
            .transform(Transformers.objectToString())
            .log()
            .handle<String> { message, headers ->
                val connId = headers[IpHeaders.CONNECTION_ID] as String
//                logger.info("connId = $connId")
                message.reversed()
//                connectionId = connId

//                val packet = parse(message)
//                if (packet != null) {
//                    val reply = handleTrackerMessage(packet, connId)
//                    reply
//                } else {
//                    null
//                }


            }
            .channel(tcpOutboundChannel)
            .get()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun tcpOutboundFlow(): IntegrationFlow = IntegrationFlow.from(tcpOutboundChannel)
        .log()
        .enrichHeaders { h -> h.headerFunction<String>(IpHeaders.CONNECTION_ID) {
            logger.info("header function, message = ${it.payload}")
            logger.info("message header = ${it.headers}")
            it.headers[IpHeaders.CONNECTION_ID] ?: "19823091820938"
        }}
        .handle(tcpSendingMessageHandler)
        .get()
}

