package com.evmonitor.application.imports.xpeng;

import java.util.UUID;

/** Wird nach dem Commit eines neuen Import-Jobs veroeffentlicht und weckt den Worker sofort auf. */
public record XpengImportJobQueuedEvent(UUID jobId) {}
