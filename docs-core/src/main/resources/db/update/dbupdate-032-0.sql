CREATE TABLE T_REGISTRATION_REQUEST (
                                        REQ_ID_C VARCHAR(36) PRIMARY KEY,
                                        REQ_USERNAME_C VARCHAR(50) NOT NULL,
                                        REQ_PASSWORD_C VARCHAR(100) NOT NULL,
                                        REQ_EMAIL_C VARCHAR(100) NOT NULL,
                                        REQ_STATUS_C VARCHAR(20) NOT NULL, -- PENDING/APPROVED/REJECTED
                                        REQ_CREATEDATE_D DATETIME NOT NULL,
                                        REQ_DELETEDATE_D DATETIME
);