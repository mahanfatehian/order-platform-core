package helper;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "johndoe";   // change to your desired password
        String encoded = encoder.encode(rawPassword);
        System.out.println(encoded);
    }
}