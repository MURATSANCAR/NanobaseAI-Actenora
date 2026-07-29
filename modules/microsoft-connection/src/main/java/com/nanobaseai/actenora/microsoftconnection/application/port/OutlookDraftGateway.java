package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;

import java.util.UUID;

public interface OutlookDraftGateway {

    OutlookDraftResult create(UUID tenantId, OutlookDraftRequest request);
}
