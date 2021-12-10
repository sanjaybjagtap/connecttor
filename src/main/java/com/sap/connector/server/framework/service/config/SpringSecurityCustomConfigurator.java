package com.sap.connector.server.framework.service.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static com.sap.connector.server.framework.service.beans.ApplicationContants.ROLE_CONSOLE_USER;

@Configuration
//@EnableWebSecurity
public class SpringSecurityCustomConfigurator extends WebSecurityConfigurerAdapter {

    @Autowired
    CustomAuthenticationProvider authProvider;

    @Autowired
    CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Value("${ocb.console.user}")
    String consoleUser;

    @Value("${ocb.console.pass}")
    String consolePass;

    @Value("${ocb.console.enabled}")
    String consoleEnabled;

    @Value("${ocb.console.unsecure}")
    String unsecureConsole;

    @Override
    protected void configure(HttpSecurity auth) throws Exception {
        auth.authorizeRequests()
                //.antMatchers("/securityNone").permitAll()
                //.antMatchers("/login").authenticated()
                .antMatchers("/gs-guide-websocket")
                    .permitAll();
                if(!Boolean.parseBoolean(consoleEnabled)) {
                    auth.authorizeRequests()
                     .antMatchers("/console/**")
                        .denyAll()
                    .antMatchers("/console_wp/**")
                        .denyAll();
                } else if(Boolean.parseBoolean(consoleEnabled) && Boolean.parseBoolean(unsecureConsole)) {
                    auth.authorizeRequests()
                            .antMatchers("/console/**")
                                .permitAll()
                            .antMatchers("/console_wp/**")
                                .permitAll();
                } else {
                    auth.authorizeRequests()
                            .antMatchers("/console/**")
                                .hasRole(ROLE_CONSOLE_USER)
                            .antMatchers("/console_wp/**")
                                .hasRole(ROLE_CONSOLE_USER)
                            .and()
                            .formLogin()
                            .and()
                            .logout()
                            .logoutUrl("/console_wp/logout")
                            .logoutSuccessUrl("/console")
                            .invalidateHttpSession(true);
                }
                    auth
                        .authorizeRequests()
                        .anyRequest()
                        .authenticated();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .passwordEncoder(NoOpPasswordEncoder.getInstance())
                .withUser(consoleUser)
                .password(consolePass)
                .roles(ROLE_CONSOLE_USER);
    }

    @Override
    public UserDetailsService userDetailsService() {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withDefaultPasswordEncoder()
                .username(consoleUser)
                .password(consolePass)
                .roles(ROLE_CONSOLE_USER)
                .build());
        return manager;
    }

}
