package com.mapmates.common.security;
import com.mapmates.common.config.AppConfig;import com.mapmates.common.db.*;import jakarta.mail.*;import jakarta.mail.internet.*;import java.util.*;import java.util.concurrent.*;
public final class ResetMail {
 private static final ThreadPoolExecutor queue=new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(100),r->{Thread t=new Thread(r,"mapmates-reset-mail");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public static void enqueue(String email,String token,String hash){try{queue.execute(()->{boolean sent=false;for(int attempt=0;attempt<2&&!sent;attempt++){try{if(Tx.read(c->Sql.one(c,"SELECT 1 FROM password_reset_tokens WHERE token_hash=? AND used_at IS NULL AND expires_at>NOW()",hash))==null)return;send(email,token);sent=true;}catch(Exception e){java.util.logging.Logger.getLogger(ResetMail.class.getName()).warning("Password reset mail delivery attempt failed");}}final String status=sent?"sent":"failed";Tx.write(c->Sql.update(c,"UPDATE password_reset_tokens SET delivery_status=? WHERE token_hash=?",status,hash));});}catch(RejectedExecutionException e){Tx.write(c->Sql.update(c,"UPDATE password_reset_tokens SET delivery_status='failed' WHERE token_hash=?",hash));}}
 private static void send(String email,String token)throws Exception {
  var properties=new Properties();String user=AppConfig.get("smtp.user","");
  properties.setProperty("mail.smtp.host",AppConfig.require("smtp.host"));properties.setProperty("mail.smtp.port",AppConfig.get("smtp.port","587"));properties.setProperty("mail.smtp.auth",String.valueOf(!user.isBlank()));properties.setProperty("mail.smtp.starttls.enable",AppConfig.get("smtp.starttls","true"));properties.setProperty("mail.smtp.starttls.required",AppConfig.get("smtp.starttls","true"));properties.setProperty("mail.smtp.ssl.checkserveridentity","true");properties.setProperty("mail.smtp.connectiontimeout","5000");properties.setProperty("mail.smtp.timeout","5000");properties.setProperty("mail.smtp.writetimeout","5000");
  var session=Session.getInstance(properties,user.isBlank()?null:new Authenticator(){protected PasswordAuthentication getPasswordAuthentication(){return new PasswordAuthentication(user,AppConfig.get("smtp.password",""));}});
  var message=new MimeMessage(session);message.setFrom(new InternetAddress(AppConfig.get("smtp.from","no-reply@mapmates.test")));message.setRecipients(Message.RecipientType.TO,InternetAddress.parse(email,false));message.setSubject("MapMates 비밀번호 재설정","UTF-8");
  String url=AppConfig.require("public.url").replaceAll("/$","")+"/signup.html?reset="+token;
  message.setText("아래 링크에서 20분 안에 비밀번호를 재설정해주세요. 링크는 한 번만 사용할 수 있습니다.\n\n"+url+"\n\n요청하지 않았다면 이 메일을 무시해주세요.","UTF-8");Transport.send(message);
 }
 public static void close(){queue.shutdownNow();}
}
