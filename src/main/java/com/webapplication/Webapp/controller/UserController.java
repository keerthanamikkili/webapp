package com.webapplication.Webapp.controller;

import com.webapplication.Webapp.entity.User;
import com.webapplication.Webapp.entity.UserResponse;
import com.webapplication.Webapp.repository.UserRepository;
import com.webapplication.Webapp.service.HealthCheckService;
import com.webapplication.Webapp.service.PubSubPublisher;
import com.webapplication.Webapp.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
public class UserController {

    @Autowired
    public UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HealthCheckService healthCheckService;

    private static final Logger log = LogManager.getLogger(UserController.class);

    @Autowired
    private PubSubPublisher pubSubPublisher;

    // @Autowired
    // private EmailService emailService; // Assuming you have an EmailService to
    // send emails

    @GetMapping("/v1/user/self")
    public ResponseEntity<UserResponse> fetchUserDetails(@RequestHeader("Authorization") String header,
            HttpServletRequest request) {
        try {
            ThreadContext.put("severity", "INFO");
            ThreadContext.put("labels", "FetchUserInfo");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.info("Fetching user details...");
            if (!healthCheckService.VerifyDatabaseConnection()) {
                ThreadContext.put("severity", "ERROR");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.error("Database connection not available.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
            }

            String token = null;
            String base64Credentials = header.substring("Basic".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);
            System.out.println("credentials" + credentials);
            String username = parts[0];
            String password = parts[1];
            System.out.println("username: " + username);
            ThreadContext.put("severity", "DEBUG");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.debug("Username extracted from credentials: " + username);
            User user = userRepository.findByUsername(username);
            if (user == null) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("User not found for username: " + username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (!user.isVerified()) {
                // User account not verified
                // Log and return forbidden response
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("User account not verified for username: " + username);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            boolean ValidCredentials = userService.ValidCredentials(username, password);
            if (ValidCredentials) {
                ThreadContext.put("severity", "INFO");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.info("User authenticated successfully.");
                UserResponse userResponse = UserResponse.convertToDTO(user);
                return ResponseEntity.ok().body(userResponse);
            } else {
                ThreadContext.put("severity", "ERROR");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.error("Invalid credentials provided.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (Exception e) {
            ThreadContext.put("severity", "ERROR");
            ThreadContext.put("labels", "FetchUserInfo");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.error("Error fetching user details.", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/v1/user")
    public ResponseEntity<Object> createUser(@RequestBody User newUser, HttpServletRequest request) {
        try {
            ThreadContext.put("severity", "INFO");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.info("Creating new user...");
            if (!healthCheckService.VerifyDatabaseConnection()) {
                ThreadContext.put("severity", "ERROR");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.error("Database connection not available.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
            }

            if (newUser.getUsername() == null || newUser.getUsername().isEmpty()) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("Email id is mandatory for user creation.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Email id is mandatory\"}");
            }

            if (newUser.getPassword() == null || newUser.getPassword().isEmpty()) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("Password is mandatory for user creation.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Password is mandatory\"}");
            }

            if (newUser.getFirst_name() == null || newUser.getFirst_name().isEmpty()) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("First Name is mandatory for user creation.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"First Name is mandatory\"}");
            }
            if (newUser.getLast_name() == null || newUser.getLast_name().isEmpty()) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("Last Name is mandatory for user creation.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Last Name is mandatory\"}");
            }

            if (!CheckValidEmail(newUser.getUsername())) {
                ThreadContext.put("severity", "DEBUG");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.debug("Invalid email address provided");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Invalid Email Address\"}");
            }

            if (newUser.getPassword() != null && !CheckValidPassword(newUser.getPassword())) {
                ThreadContext.put("severity", "DEBUG");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.debug("Invalid password format provided.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(
                        "{\"error\": \"Invalid password. Password should contain atleast one uppercase, one lowercase, and one digit and minimum length of 8\"}");
            }
            String token = UUID.randomUUID().toString();
            newUser.setVerificationToken(token);
            userService.createUser(newUser);
            ThreadContext.put("severity", "INFO");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.info("User created successfully.");
            String verificationLink = generateVerificationLink(newUser.getVerificationToken().toString());
            pubSubPublisher.publishUserInformation(newUser,verificationLink);

            // Generate the verification link with a 2-minute expiration
            // String verificationLink = generateVerificationLink(newUser.getVerificationToken().toString());
            System.out.println(verificationLink);
            UserResponse userResponse = UserResponse.convertToDTO(newUser);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(userResponse);
        } catch (Exception e) {
            ThreadContext.put("severity", "ERROR");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.error("Error creating user.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"User Already Exists!!\"}");
        }
    }

    @PutMapping("/v1/user/self")
    public ResponseEntity<Object> updatingUser(@RequestBody User newUser, @RequestHeader("Authorization") String header,
            HttpServletRequest request) {
        try {
            ThreadContext.put("severity", "INFO");
            ThreadContext.put("labels", "UpdatingUserInfo");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.info("Updating user...");
            String Base64Credentials = header.substring("Basic ".length()).trim();
            String DecodedCredentials = new String(Base64.getDecoder().decode(Base64Credentials),
                    StandardCharsets.UTF_8);
            String[] splitValues = DecodedCredentials.split(":", 2);

            String username = splitValues[0];
            String password = splitValues[1];
            User user = userRepository.findByUsername(username);

            if (user == null) {
                ThreadContext.put("severity", "ERROR");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.error("Not authorized to update others details");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            if (!user.isVerified()) {
                // User account not verified
                // Log and return forbidden response
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("User account not verified for username: " + username);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            
            boolean isValidCredentials = userService.ValidCredentials(username, password);

            if (!isValidCredentials) {
                ThreadContext.put("severity", "ERROR");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.error("Not authorized to update others details");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            if (newUser.getUsername() != null && !newUser.getUsername().isEmpty() ||
                    newUser.getAccount_updated() != null ||
                    newUser.getAccount_created() != null ||
                    newUser.getId() != null) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn(
                        "Username, account_updated, account_created, and id fields should not be provided in the payload.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Username, account_updated, account_created, and id fields should not be provided in the payload.\"}");
            }

            if (!ValidUpdateRequest(newUser)) {
                ThreadContext.put("severity", "ERROR");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.error("Invalid update request.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Invalid update request.\"}");
            }

            if (newUser.getPassword() == null || newUser.getPassword().isEmpty()) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("Password is mandatory for user update.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Password is mandatory\"}");
            }

            if (newUser.getFirst_name() == null || newUser.getFirst_name().isEmpty()) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("First Name is mandatory for user update.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"First Name is mandatory\"}");

            }

            if (newUser.getLast_name() == null || newUser.getLast_name().isEmpty()) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn("Last Name is mandatory for user update.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Last Name is mandatory\"}");
            }

            if (newUser.getPassword() != null && !CheckValidPassword(newUser.getPassword())) {
                ThreadContext.put("severity", "WARN");
                ThreadContext.put("httpMethod", request.getMethod());
                ThreadContext.put("path", request.getRequestURI());
                log.warn(
                        "Invalid password format provided for user update. Password should contain atleast one uppercase, one lowercase, and one digit and minimum length of 8");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(
                        "{\"error\": \"Invalid password. Password should contain atleast one uppercase, one lowercase, and one digit and minimum length of 8\"}");
            }

            updateUser(user, newUser);

            // Save updated user
            user.setAccount_updated(LocalDateTime.now());
            userRepository.save(user);
            ThreadContext.put("severity", "INFO");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.info("User updated successfully.");
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            ThreadContext.put("severity", "ERROR");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.error("Error updating user.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @RequestMapping(value = "/v1/user/self", method = { RequestMethod.POST, RequestMethod.PATCH, RequestMethod.DELETE,
            RequestMethod.HEAD, RequestMethod.OPTIONS })
    public ResponseEntity<Void> ExceptGetAndPutInvalidMethod(HttpServletRequest request) {
        ThreadContext.put("severity", "WARN");
        ThreadContext.put("httpMethod", request.getMethod());
        ThreadContext.put("path", request.getRequestURI());
        log.warn("Received invalid method request for /v1/user/self: " + request.getMethod());
        if (!healthCheckService.VerifyDatabaseConnection()) {
            ThreadContext.put("severity", "ERROR");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.error("Database connection not available.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .cacheControl(CacheControl.noCache())
                .build();
    }

    @RequestMapping(value = "/v1/user", method = { RequestMethod.GET, RequestMethod.PATCH, RequestMethod.DELETE,
            RequestMethod.HEAD, RequestMethod.OPTIONS })
    public ResponseEntity<Void> ExceptPostInvalidMethod(HttpServletRequest request) {
        ThreadContext.put("severity", "WARN");
        ThreadContext.put("httpMethod", request.getMethod());
        ThreadContext.put("path", request.getRequestURI());
        log.warn("Received invalid method request for /v1/user: " + request.getMethod());
        if (!healthCheckService.VerifyDatabaseConnection()) {
            ThreadContext.put("severity", "ERROR");
            ThreadContext.put("httpMethod", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            log.error("Database connection not available.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .cacheControl(CacheControl.noCache())
                .build();
    }

    private boolean CheckValidEmail(String email) {
        String regex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    private boolean CheckValidPassword(String password) {
        String regex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$";
        return password.matches(regex);
    }

    private boolean ValidUpdateRequest(User newUser) {
        return newUser.getUsername() == null &&
                newUser.getAccount_updated() == null &&
                newUser.getAccount_created() == null &&
                newUser.getId() == null;
    }

    private void updateUser(User user, User newUser) {
        if (newUser.getFirst_name() != null && !newUser.getFirst_name().isEmpty()) {
            user.setFirst_name(newUser.getFirst_name());
        }
        if (newUser.getLast_name() != null && !newUser.getLast_name().isEmpty()) {
            user.setLast_name(newUser.getLast_name());
        }
        if (newUser.getPassword() != null && !newUser.getPassword().isEmpty()
                && CheckValidPassword(newUser.getPassword())) {
            user.setPassword(new BCryptPasswordEncoder().encode(newUser.getPassword()));
        }
    }

    // @GetMapping("/verify-email")
    // public ResponseEntity<String> verifyEmail(@QueryParam("token") String token,
    // @QueryParam("expiration") String expiration) {
    // try {
    // User user = userRepository.findByVerificationToken(token,);
    // if (user != null) {
    // // If user is found with the provided verification token, mark the user as
    // verified
    // user.setVerified(true);
    // user.setVerificationToken(null); // Clear the verification token
    // userRepository.save(user);
    // return ResponseEntity.ok("Email verified successfully.");
    // } else {
    // // If user is not found, return an error message
    // return ResponseEntity.badRequest().body("Invalid verification token.");
    // }
    // } catch (Exception e) {
    // // Handle any exceptions and return an error response
    // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error
    // verifying email.");
    // }
    // }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token,
            @RequestParam("expiration") String expiration) {
        try {
            User user = userRepository.findByVerificationToken(token);
            if (user != null) {
                // Check if the token is expired
                // LocalDateTime expirationTime = LocalDateTime.parse(expiration, DateTimeFormatter.ISO_DATE_TIME);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                LocalDateTime expirationTime = LocalDateTime.parse(expiration, formatter);
                LocalDateTime currentTime = LocalDateTime.now();
                if (expirationTime.isAfter(currentTime)) {
                    // If user is found with the provided verification token and token is not
                    // expired, mark the user as verified
                    user.setVerified(true);
                    user.setVerificationToken(null); // Clear the verification token
                    userRepository.save(user);
                    return ResponseEntity.ok("Email verified successfully.");
                } else {
                    // If the token is expired, return an error message
                    return ResponseEntity.badRequest().body("Verification token has expired.");
                }
            } else {
                // If user is not found, return an error message
                return ResponseEntity.badRequest().body("Invalid verification token.");
            }
        } catch (Exception e) {
            // Handle any exceptions and return an error response
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error verifying email.");
        }
    }

    private String generateVerificationLink(String token) {
        LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(2); // Link expires after 2 minutes

        // Format the expiration time as a string in a suitable format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String formattedExpirationTime = expirationTime.format(formatter);

        // Include the expiration time in the verification link
        return "https://mg.keerthanamikkili.me:8080/verify-email?token=" + token + "&expiration="
                + formattedExpirationTime;
    }
}
// @GetMapping("/v1/user/verify-email")
// public ResponseEntity<String> sendVerificationEmail(@RequestParam("email")
// String email, HttpServletResponse response) {
// try {
// User user = userRepository.findByUsername(email);
// if (user == null) {
// return ResponseEntity.badRequest().body("User not found.");
// }

// // Generate verification token
// UUID verificationToken = UUID.randomUUID();
// user.setVerificationToken(verificationToken);
// userRepository.save(user);

// // Construct verification link
// String verificationLink =
// "https://mg.keerthanamikkili.me:8080/verify-email?token=" +
// verificationToken;

// // Send verification email
// emailService.sendVerificationEmail(email, verificationLink);

// return ResponseEntity.ok("Verification email sent successfully.");
// } catch (Exception e) {
// return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error
// sending verification email.");
// }
// }

// @GetMapping("/v1/user/verify-email")
// public ResponseEntity<String> verifyEmail(@RequestParam("token") UUID token)
// {
// try {
// User user = userRepository.findByVerificationToken(token);
// if (user != null) {
// // If user is found with the provided verification token, mark the user as
// verified
// user.setVerified(true);
// user.setVerificationToken(null); // Clear the verification token
// userRepository.save(user);
// return ResponseEntity.ok("Email verified successfully.");
// } else {
// // If user is not found, return an error message
// return ResponseEntity.badRequest().body("Invalid verification token.");
// }
// } catch (Exception e) {
// // Handle any exceptions and return an error response
// return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error
// verifying email.");
// }
// }
// }
