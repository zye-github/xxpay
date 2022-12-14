package org.xxpay.mbr.secruity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter{

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Autowired
    private UserDetailsService userDetailsService;

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Autowired
    public void configureAuthentication(AuthenticationManagerBuilder authenticationManagerBuilder) throws Exception {
        authenticationManagerBuilder
                .userDetailsService(this.userDetailsService)
                .passwordEncoder(passwordEncoder());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
		     	// ??????????????????JWT????????????????????????csrf
		        .csrf().disable()

                .cors().and()

		        .exceptionHandling().authenticationEntryPoint(unauthorizedHandler).and()
		
		        // ??????token??????????????????session
		        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
		
		        .authorizeRequests()
		        
		        // ???????????????????????????????????????????????????
		        .anyRequest().authenticated();
		
		        // ??????JWT filter
		        httpSecurity.addFilterBefore(new JwtAuthenticationTokenFilter(), UsernamePasswordAuthenticationFilter.class);

                //?????????????????????????????? filter
                // httpSecurity.addFilterAfter(new UserSecurityTypeFilter(), UsernamePasswordAuthenticationFilter.class);
        // ????????????
        httpSecurity.headers().cacheControl();
    }
    
    @Override
    public void configure(WebSecurity web) throws Exception {
        //ignore?????? ??? ????????????spring security ??????
    	// 1.????????????????????????????????????????????????
        // 2.????????????token???rest api?????????????????????
        web.ignoring().antMatchers(
                HttpMethod.GET,
                "/",
                "/*.html",
                "/favicon.ico",
                "/**/*.html#/",
                "/**/*.html*",
                "/**/*.css",
                "/**/*.js",
                "/**/*.png",
                "/**/*.jpg",
                "/**/*.xls")
        .antMatchers(
        		"/api/auth/**",
        	    "/api/payment/**",
                "/api/mchGoods/**",//????????????
                "/api/mchGoods_category/**",//????????????
                "/api/store_banner/list",//?????????
                "/api/mini_config/vajra",//?????????
                "/api/mini_config/hot_search_list ",//??????
                "/api/mini_config/getVisualable",//????????????????????????
                "/api/wx_mini/get_live_info",//??????
        	    "/api/notify/**",
        	    "/html/**",
                "/**/*.txt");    //???????????????????????????
    }
    
}
