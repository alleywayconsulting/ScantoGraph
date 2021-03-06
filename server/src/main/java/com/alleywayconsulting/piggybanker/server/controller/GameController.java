package com.alleywayconsulting.piggybanker.server.controller;

import com.alleywayconsulting.piggybanker.server.dto.CoinDTO;
import com.alleywayconsulting.piggybanker.server.dto.GameDTO;
import com.alleywayconsulting.piggybanker.server.exceptions.AccountNotFoundException;
import com.alleywayconsulting.piggybanker.server.exceptions.MaxDepositsException;
import com.alleywayconsulting.piggybanker.server.model.Deposit;
import com.alleywayconsulting.piggybanker.server.service.BarcodeService;
import com.alleywayconsulting.piggybanker.server.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Michael Lake on 2/19/16.
 * Copyright (c) Alleyway Consulting, LLC
 */

@RestController
@RequestMapping("/api/game")
public class GameController {

    private static final Logger LOG = LoggerFactory.getLogger(GameController.class);

    @Autowired
    BarcodeService barcodeService;

    @Autowired
    AccountService accountService;

    @Autowired
    private SimpMessagingTemplate template;

    @Value("${host.address}")
    private String hostAddress;


    @RequestMapping(value = "/start/{sessionId}", method = RequestMethod.GET)
    public void start(HttpServletResponse response, @PathVariable Long sessionId) throws Exception {
        LOG.info("Start game");

        GameDTO gameDTO = new GameDTO(sessionId, "START");

        accountService.createAccount(sessionId);

        template.convertAndSend("/topic/message", gameDTO);
        response.getWriter().print(sessionId);
        sendNextCoin(sessionId);
    }

    @RequestMapping(value = "/startcode/{sessionId}", method = RequestMethod.GET, produces = "image/svg+xml")
    public void startCode(HttpServletResponse response, @PathVariable String sessionId) {
        LOG.info("Creating new game barcode");
        try {
            response.setHeader("Content-Type", "image/svg+xml");

            StringBuilder sb = new StringBuilder();
            sb.append("http://");
            if (hostAddress.equals("auto")) {
                sb.append(InetAddress.getLocalHost().getHostAddress());

            } else {
                sb.append(hostAddress);
            }

            sb.append(":8089/api/game/start/");
            sb.append(sessionId);

            String code = barcodeService.createSVGBarcode(sb.toString());

            FileCopyUtils.copy(code.getBytes(), response.getOutputStream());
        } catch (Exception e) {
            LOG.error("Unable to create SGV", e);
        }
    }

    @RequestMapping(value = "/nextcoin/{sessionId}", method = RequestMethod.GET)
    public void startCode(HttpServletResponse response, @PathVariable Long sessionId) throws Exception {

        sendNextCoin(sessionId);

    }

    private void sendNextCoin(Long sessionId) throws Exception {

        Deposit deposit = accountService.getNextDepositForAccount(sessionId);

        try {
            accountService.depositToAccount(sessionId, deposit);
            String barcodeXML = barcodeService.createSVGBarcode(deposit.getQRCodeValue());
            CoinDTO coin = new CoinDTO(sessionId, deposit.getAmount(), deposit.getDayOfWeek(), barcodeXML);
            template.convertAndSend("/topic/message", coin);

        } catch (AccountNotFoundException e) {
            GameDTO gameDTO = new GameDTO(sessionId, "RESET");
            template.convertAndSend("/topic/message", gameDTO);
        } catch (MaxDepositsException e) {
            GameDTO gameDTO = new GameDTO(sessionId, "RESET");
            template.convertAndSend("/topic/message", gameDTO);
        }
    }


}
