package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendResult;

import java.util.UUID;

/**
 * Microsoft Graph mail send (used by delivery adapters; credentials stay in this BC).
 */
public interface MailGateway {

    MailSendResult send(UUID tenantId, MailSendRequest request);
}
