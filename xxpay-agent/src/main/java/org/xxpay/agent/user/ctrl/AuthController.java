package org.xxpay.agent.user.ctrl;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xxpay.agent.common.ctrl.BaseController;
import org.xxpay.agent.common.service.RpcCommonService;
import org.xxpay.agent.user.service.UserService;
import org.xxpay.core.common.Exception.ServiceException;
import org.xxpay.core.common.annotation.MethodLog;
import org.xxpay.core.common.constant.Constant;
import org.xxpay.core.common.constant.MchConstant;
import org.xxpay.core.common.constant.RetEnum;
import org.xxpay.core.common.domain.BizResponse;
import org.xxpay.core.common.domain.XxPayResponse;
import org.xxpay.core.common.util.DateUtil;
import org.xxpay.core.common.util.MD5Util;
import org.xxpay.core.common.util.MyLog;
import org.xxpay.core.common.util.RandomValidateCodeUtil;
import org.xxpay.core.common.vo.JWTBaseUser;
import org.xxpay.core.common.vo.JWTPayload;
import org.xxpay.core.common.vo.JWTUtils;
import org.xxpay.core.entity.AgentInfo;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RequestMapping(Constant.AGENT_CONTROLLER_ROOT_PATH)
@RestController
public class AuthController extends BaseController {

    @Value("${jwt.cookie}")
    private String tokenCookie;

    @Autowired
    private UserService userService;

    @Autowired
    private RpcCommonService rpcCommonService;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserDetailsService userDetailsService;

    private static final MyLog _log = MyLog.getLog(AuthController.class);

    /**
     * ????????????
     * @return
     * @throws AuthenticationException
     */
    @RequestMapping(value = "/auth")
    @MethodLog( remark = "??????" )
    public ResponseEntity<?> authToken() throws AuthenticationException{

        String loginType = getValString("loginType");
        String vercodeRandomStr = null;
        if (MchConstant.LOGIN_TYPE_WEB.equals(loginType)) {
            String vercode = getValStringRequired("vercode"); //???????????????
            vercodeRandomStr = getValStringRequired("vercodeRandomStr"); //??????????????? ??????key

            String vercodeCacheValue = stringRedisTemplate.opsForValue().get(MchConstant.CACHEKEY_VERCODE_PREFIX_AGENT + vercodeRandomStr);

            if (!vercode.equals(vercodeCacheValue)) {
                return ResponseEntity.ok(BizResponse.build(RetEnum.RET_COMM_VERCODE_ERROR));
            }
        }
        
        String username = getValStringRequired( "username");
        String password = getValStringRequired( "password");


        try {
            String token = userService.login(username, password, MchConstant.LOGIN_TYPE_WEB, null);

            //?????????app???????????????token????????????
            if (StringUtils.isNotBlank(loginType) && (MchConstant.LOGIN_TYPE_APP.equals(loginType))) {
                stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_TOKEN_PREFIX_AGENT + token, "1",
                        MchConstant.APP_CACHE_TOKEN_TIMEOUT, TimeUnit.SECONDS);
                JSONObject data = new JSONObject();
                data.put("access_token", token);
                return ResponseEntity.ok(XxPayResponse.buildSuccess(data));
            }

            //web??????,???????????????????????????
            if (MchConstant.LOGIN_TYPE_WEB.equals(loginType)) {
                stringRedisTemplate.delete(MchConstant.CACHEKEY_VERCODE_PREFIX_AGENT + vercodeRandomStr);
            }
            stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_TOKEN_PREFIX_AGENT + token, "1",
                    MchConstant.CACHE_TOKEN_TIMEOUT, TimeUnit.SECONDS);
            JSONObject data = new JSONObject();
            data.put("access_token", token);
            return ResponseEntity.ok(XxPayResponse.buildSuccess(data));

        }catch (ServiceException e) {
            return ResponseEntity.ok(BizResponse.build(e.getRetEnum()));
        }
    }

    /**
     * ????????????(???????????????????????????????????????)
     * @return
     * @throws AuthenticationException
     */
    @RequestMapping(value = "/mgr_auth")
    public ResponseEntity<?> mgrAuthToken() throws AuthenticationException{

        Long agentId = getValLongRequired("agentId");
        String token = getValStringRequired( "token");

        AgentInfo agentInfo = userService.findByAgentId(agentId);
        if(agentInfo == null) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_SERVICE_AGENT_NOT_EXIST));
        }
        if(MchConstant.PUB_YES != agentInfo.getStatus()) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_AGENT_STATUS_STOP));
        }

        // ?????????????????????????????????token,????????????
        // ?????????ID+????????????????????????+?????? ???32???MD5???????????????
        String loginUserName = agentInfo.getLoginUserName();
        String secret = "Abc%$G&!!!128G";
        
		String signNowTime = DateUtil.date2Str(new Date(), "ddHHmm"); //????????????
		String rawTokenByNow = agentId + loginUserName + secret + signNowTime;
		String md5Now = MD5Util.string2MD5(rawTokenByNow).toUpperCase();
		
		String sign1Time = DateUtil.date2Str(DateUtil.minusDateByMinute(new Date(), 1 ), "ddHHmm"); //??????????????????1min
		String rawTokenBySub1 = agentId + loginUserName + secret + sign1Time;
        String md5BySub1 = MD5Util.string2MD5(rawTokenBySub1).toUpperCase();
        
        //?????????????????? ??? ???????????????1???????????????????????????59s ???????????????????????????
        if(!md5Now.equals(token) && !md5BySub1.equals(token)) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_MCH_ILLEGAL_LOGIN));
        }

        //????????????
        JWTBaseUser jwtBaseUser = (JWTBaseUser) userDetailsService.loadUserByUsername(agentId.toString());
        jwtBaseUser.setLoginTypeAndVersion(MchConstant.LOGIN_TYPE_WEB, null, null); //??????????????????
        String authToken = JWTUtils.generateToken(new JWTPayload(jwtBaseUser), mainConfig.getJwtSecret(), mainConfig.getJwtExpiration());
        stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_TOKEN_PREFIX_AGENT + authToken, "1",
                MchConstant.CACHE_TOKEN_TIMEOUT, TimeUnit.SECONDS);
        
        JSONObject data = new JSONObject();
        data.put("access_token", authToken);
        return ResponseEntity.ok(XxPayResponse.buildSuccess(data));
    }

    /**
     * ?????????????????????
     */
    @RequestMapping(value = "/auth/vercode")
    public void getAuthCode( HttpServletResponse response, String vercodeRandomStr) throws IOException {
    	
    	if(StringUtils.isEmpty(vercodeRandomStr))return ;
        Map<String, Object> randomMap = RandomValidateCodeUtil.getRandcode(120, 40, 6, 20);
        String vercodeValue = randomMap.get("randomString").toString(); //??????
        stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_VERCODE_PREFIX_AGENT + vercodeRandomStr,
        									  vercodeValue, MchConstant.CACHEKEY_VERCODE_TIMEOUT, TimeUnit.MINUTES);
        BufferedImage randomImage = (BufferedImage)randomMap.get("randomImage");
        ImageIO.write(randomImage, "JPEG", response.getOutputStream());
    }

}
