package dev.jayhan.trackertest

import org.springframework.integration.ip.IpHeaders
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.GenericMessage
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController(
    private val tcpOutboundChannel: MessageChannel,
) {
    @PostMapping("sendtest")
    fun aa(@RequestParam connId: String) {
        tcpOutboundChannel.send(GenericMessage<String>("aaaaaa"))
    }
}
