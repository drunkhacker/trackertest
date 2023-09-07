package dev.jayhan.trackertest

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.AbstractMessageChannel
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.endpoint.MessageProducerSupport
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter
import org.springframework.integration.ip.tcp.TcpSendingMessageHandler
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory
import org.springframework.integration.ip.tcp.connection.TcpNioServerConnectionFactory
import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer
import org.springframework.integration.ip.tcp.serializer.ByteArrayLfSerializer
import org.springframework.messaging.MessageHandler

@Configuration
class TcpIntegrationConfig {
    private val logger = LoggerFactory.getLogger(this::class.java)

//    @Bean
//    fun threadConnectionFactory(): ThreadAffinityClientConnectionFactory {
//        return ThreadAff
//    }
//
//    @Bean
//    fun clientFactory(): AbstractClientConnectionFactory {
//        Tcp.netClient()
//    }

    @Bean
    fun connectionFactory(): AbstractServerConnectionFactory {
        val connectionFactory = TcpNioServerConnectionFactory(9988)
        val ser = ByteArrayLfSerializer()
        connectionFactory.serializer = ser
        connectionFactory.deserializer = ser
        connectionFactory.isSingleUse = false
        return connectionFactory
    }

//    @Bean
//    fun inboundChannel(): MessageChannel {
//        return DirectChannel()
//    }
//
//    @Bean
//    fun replyChannel(): MessageChannel {
//        return DirectChannel()
//    }

    @Bean
    fun replyChannel(): DirectChannel {
        return DirectChannel()
    }

    @Bean
    fun tcpOutboundChannel(): AbstractMessageChannel {
        val chann = DirectChannel()
        chann.componentName = "tcpOutboundChannel"
        return chann
    }

    @Bean
    fun tcpChannelAdapter(
        connectionFactory: AbstractServerConnectionFactory
    ): MessageProducerSupport {
        val adapter = TcpReceivingChannelAdapter()
        adapter.setConnectionFactory(connectionFactory)
        return adapter
    }

    @Bean
    fun tcpSendingMessageHandler(
        connectionFactory: AbstractServerConnectionFactory,
    ): MessageHandler {
        val messageHandler = TcpSendingMessageHandler()
        messageHandler.setConnectionFactory(connectionFactory)
        return messageHandler
    }
}
