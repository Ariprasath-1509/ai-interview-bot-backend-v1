package com.benchreadiness.auth.service;

/** Why a login was rejected. Written to the log and the audit entry; never shown to the client. */
public enum LoginFailureReason {
    UNKNOWN_ACCOUNT,
    WRONG_PASSWORD,
    LOCKED,
    INACTIVE_ACCOUNT
}
