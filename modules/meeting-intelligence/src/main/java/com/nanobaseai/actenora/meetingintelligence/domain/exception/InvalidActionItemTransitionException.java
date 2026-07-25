package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class InvalidActionItemTransitionException extends ActenoraException {

    public InvalidActionItemTransitionException(ActionItemStatus from, ActionItemStatus to) {
        super(
                "INVALID_ACTION_ITEM_TRANSITION",
                "Cannot transition action item from " + from + " to " + to
        );
    }
}
