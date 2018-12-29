package com.warrencrasta.varo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Varo {
	private static final Logger logger = LoggerFactory.getLogger(Varo.class);
	
	public static void main(String[] args) throws InterruptedException {
		logger.info("Program started");
		Properties props = loadProperties();
		while (true) {
			connectAndReadEmails(props);
			Thread.sleep(60000);
		}
	}
	
	private static Properties loadProperties() {
		Properties props = new Properties();
		InputStream input = null;
		try {
			logger.info("Loading properties");
			ClassLoader classloader = Thread.currentThread().getContextClassLoader();
			input = classloader.getResourceAsStream("config.properties");
			props.load(input);
		} catch (IOException e) {
			logger.error("Could not load properties. Exception: ", e);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					logger.error("Could not close properties file. Exception: ", e);
				}
			}
		}
		return props;
	}
	
	private static void connectAndReadEmails(Properties props) {
		logger.info("Connecting to inbox");
		Session session = Session.getInstance(props,
				new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(props.getProperty("email.address"), props.getProperty("email.password"));
		            }
				});
		Store store = null;
		Folder inbox = null;
		try {
			store = session.getStore(props.getProperty("protocol"));
			store.connect(props.getProperty("mail.smtp.host"), props.getProperty("email.address"), props.getProperty("email.password"));
			inbox = store.getFolder("inbox");
			inbox.open(Folder.READ_WRITE);
		} catch (MessagingException e) {
			logger.error("Could not create or connect to store/inbox. Exception: ", e);
		}
		
		logger.info("Iterating through unseen messages");
		// search for all "unseen" messages
		Flags seen = new Flags(Flags.Flag.SEEN);
		FlagTerm unseenFlagTerm = new FlagTerm(seen, false);
		Message messages[] = null;
		try {
			messages = inbox.search(unseenFlagTerm);
		} catch (MessagingException e) {
			logger.error("Could not retrieve messages. Exception: ", e);
		}
		
		if (messages.length == 0) {
			logger.info("No messages found");
		};
		
		try {
			for (int i = 0; i < messages.length; i++) {
				if(messages[i].getSubject().contains("New Comment On Varo Money Referral Post")) {
					List<String> emailAddresses = null;
					try {
						emailAddresses= getEmailAddresses(getText(messages[i]));
					} catch (MessagingException | IOException e) {
						logger.error("Could not get email addresses. Exception: ", e);
					}
					sendEmails(emailAddresses, session, props);
				}
				messages[i].setFlag(Flag.SEEN, true);
			}
		} catch (MessagingException e) {
			logger.error("Could not iterate through messages. Exception: ", e);
		}
		
		try {
			inbox.close(true);
			store.close();
		} catch (MessagingException e) {
			logger.error("Could not close resources. Exception: ", e);
		}
	}

	/* https://www.oracle.com/technetwork/java/javamail/faq/index.html#mainbody */
	private static String getText(Part p) throws MessagingException, IOException {

		if (p.isMimeType("text/*")) {
			String s = (String)p.getContent();
			return s;
		}

		if (p.isMimeType("multipart/alternative")) {
			// prefer html text over plain text
			Multipart mp = (Multipart)p.getContent();
			String text = null;
			for (int i = 0; i < mp.getCount(); i++) {
				Part bp = mp.getBodyPart(i);
				if (bp.isMimeType("text/plain")) {
					if (text == null)
						text = getText(bp);
					continue;
				} else if (bp.isMimeType("text/html")) {
					String s = getText(bp);
					if (s != null)
						return s;
				} else {
					return getText(bp);
				}
			}
			return text;
		} else if (p.isMimeType("multipart/*")) {
			Multipart mp = (Multipart)p.getContent();
			for (int i = 0; i < mp.getCount(); i++) {
				String s = getText(mp.getBodyPart(i));
				if (s != null)
					return s;
			}
		}
		return null;
	}

	/* Extract e-mailAddresses from a message */
	private static List<String> getEmailAddresses(String text) {
		List<String> emailAddresses = new ArrayList<>();
		Matcher m = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+").matcher(text);
		while (m.find()) {
			emailAddresses.add(m.group());
		}
		return emailAddresses;
	}

	private static void sendEmails(List<String> emailAddresses, Session session, Properties props) {
		logger.info("Sending email");
		if (emailAddresses.size() > 0) {
			Message message = new MimeMessage(session);
			try {
				message.setFrom(new InternetAddress(props.getProperty("email.address")));
				message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailAddresses.get(0)));
				message.setSubject(props.getProperty("mail.subject"));
				message.setContent(props.getProperty("mail.body"), "text/html; charset=utf-8");
			} catch (MessagingException e) {
				logger.error("Could not create message to send. E-mail address: {} Exception: ", emailAddresses.get(0), e);
			}

			try {
				Transport.send(message);
				logger.info("Email to {} sent",  emailAddresses.get(0));
			} catch (MessagingException e) {
				logger.error("Could not send message. E-mail address: {} Exception: ", emailAddresses.get(0), e);
			} 
		}
	}

}