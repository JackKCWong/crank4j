package com.danielflower.crank4j.sharedstuff;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CertificatesChecker {
    private static final Logger log = LoggerFactory.getLogger(CertificatesChecker.class);

    private static final String THERE_IS_NO_CERTIFICATES_GOING_TO_EXPIRE_IN_30_DAYS = "All certs valid for at least 30 days";
    private final String keyStorePath;
    private final String keyStorePassword;
    private JSONObject expirationDetails = new JSONObject("{status:checking cert}");
    private static CertificatesChecker certificatesChecker = null;

    public CertificatesChecker(String keyStorePath, String keyStorePassword) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        final Runnable runnable = this::updateDetails;
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(runnable, 0, 1, TimeUnit.DAYS);
    }

    public static CertificatesChecker getCertificatesChecker(String keyStorePath, String keyStorePassword) {
        if (certificatesChecker == null) {
            certificatesChecker = new CertificatesChecker(keyStorePath, keyStorePassword);
        }
        return certificatesChecker;
    }

    public JSONObject getAvailabilityDetails() {
        return expirationDetails;
    }

    public void updateDetails() {
        String result = "";
        boolean isCertOk = true;
        JSONArray urlExpiryMapping = new JSONArray();
        try {
            //load the keystore where the SSH certificates are stored
            final KeyStore sshKeyStore = KeyStore.getInstance("JCEKS");
            sshKeyStore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
            // validate that the SSH certificates are not about to expire
            final Enumeration<String> aliases = sshKeyStore.aliases();
            while (aliases.hasMoreElements()) {
                final String alias = aliases.nextElement();
                final Certificate certificate = sshKeyStore.getCertificate(alias);
                if (certificate instanceof X509Certificate) {
                    List<String> alts = getDNSSubjectAlts((X509Certificate)certificate);
                    final Date certificateExpirationDate = ((X509Certificate) certificate).getNotAfter();
                    // how many days until expiration?
                    final int daysTillExpiration = Days.daysBetween(LocalDate.now(), LocalDate.fromDateFields(certificateExpirationDate)).getDays();
                    if (daysTillExpiration < 30) {
                        result += (alias + " is going to expire in " + daysTillExpiration + " days ");
                        isCertOk = false;
                    }
	                JSONObject info = new JSONObject();
	                info.put("Expiry Date", certificateExpirationDate.toString());
	                JSONArray altsJson = new JSONArray();
	                alts.forEach(alt -> altsJson.put(alt));
	                info.put("SANs", alts);
                    JSONObject certInfo = new JSONObject();
                    certInfo.put(alias, info);
                    urlExpiryMapping.put(certInfo);
                }
            }
        } catch (Exception e) {
            log.error("can not get certificates expiration details ", e);
        }
        String info = isCertOk ? THERE_IS_NO_CERTIFICATES_GOING_TO_EXPIRE_IN_30_DAYS : result;
        expirationDetails = new JSONObject();
        expirationDetails.put("info", info);
        expirationDetails.put("details",urlExpiryMapping);
    }

    public static List<String> getDNSSubjectAlts(X509Certificate cert) {
        ArrayList subjectAltList = new ArrayList();
        Collection c = null;
        try {
            c = cert.getSubjectAlternativeNames();
        }
        catch (CertificateParsingException cpe) {
            log.debug("Failed to get Subject Alternative Names from cert " + cert);
        }
        if (c != null) {
	        for (Object aC : c) {
		        List list = (List) aC;
		        int type = (Integer) list.get(0);
		        // If type is 2, then we've got a dNSName
		        if (type == 2) {
			        String s = (String) list.get(1);
			        subjectAltList.add(s);
		        }
	        }
        }
        return subjectAltList;
    }
}
