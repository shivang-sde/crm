public interface CommunicationProvider {
    void sendSms(String to, String message);
    void sendWhatsApp(String to, String message);
    void initiateCall(String to, String ivrFlow);
}