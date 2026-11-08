package com.mapmates.member.service;
import com.mapmates.common.db.*;import com.mapmates.common.security.*;import com.mapmates.common.web.Validator;import com.mapmates.common.exception.ApiException;import java.security.*;import java.util.*;import java.nio.charset.StandardCharsets;
public final class PasswordRecovery {
 public record Start(String email){}
 public record Finish(String token,String newPassword){}
 private record Delivery(String email,String token,String hash){}
 public static String hash(String raw){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public static void start(String email){String address=Validator.requireEmail(email);RequestLimits.check("recovery-account:"+hash(address),3,900000);
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),digest=hash(token);
  var delivery=Tx.write(c->{var member=Sql.one(c,"SELECT id FROM members WHERE email=? FOR UPDATE",address);if(member==null)return null;long id=Sql.number(member,"id");Sql.update(c,"DELETE FROM password_reset_tokens WHERE member_id=?",id);Sql.update(c,"INSERT INTO password_reset_tokens(token_hash,member_id,expires_at) VALUES(?,?,DATE_ADD(NOW(),INTERVAL 20 MINUTE))",digest,id);return new Delivery(address,token,digest);});
  if(delivery!=null)ResetMail.enqueue(delivery.email(),delivery.token(),delivery.hash());
 }
 public static void finish(String token,String password){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw ApiException.badRequest("유효한 재설정 링크가 필요해요.");Validator.requirePassword(password);String digest=hash(token),passwordHash=PasswordHasher.hash(password);
  Tx.write(c->{var hint=Sql.one(c,"SELECT member_id FROM password_reset_tokens WHERE token_hash=?",digest);if(hint==null)throw ApiException.badRequest("재설정 링크가 만료됐거나 사용됐어요.");long id=Sql.number(hint,"member_id");Sql.one(c,"SELECT id FROM members WHERE id=? FOR UPDATE",id);var valid=Sql.one(c,"SELECT token_hash FROM password_reset_tokens WHERE token_hash=? AND used_at IS NULL AND expires_at>NOW() FOR UPDATE",digest);if(valid==null)throw ApiException.badRequest("재설정 링크가 만료됐거나 사용됐어요.");Sql.update(c,"UPDATE members SET password_hash=?,auth_version=auth_version+1 WHERE id=?",passwordHash,id);Sql.update(c,"UPDATE password_reset_tokens SET used_at=NOW() WHERE member_id=? AND used_at IS NULL",id);return null;});
 }
}
