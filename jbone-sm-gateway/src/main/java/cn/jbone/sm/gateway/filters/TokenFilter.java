package cn.jbone.sm.gateway.filters;

import cn.jbone.sso.common.token.JboneToken;
import cn.jbone.common.rpc.Result;
import cn.jbone.sm.gateway.constants.GatewayConstants;
import cn.jbone.sm.gateway.token.TokenRepository;
import com.alibaba.fastjson.JSON;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public class TokenFilter extends ZuulFilter {

    Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext requestContext = RequestContext.getCurrentContext();
        HttpServletRequest request = requestContext.getRequest();
        String uri = request.getRequestURI();
        return !isInWhiteList(uri);
    }

    private AntPathMatcher pathMatcher = new AntPathMatcher();

    private TokenRepository tokenRepository;

    private List<String> uriWhiteList;

    public TokenFilter(){}
    public TokenFilter(TokenRepository tokenRepository,List<String> uriWhiteList){
        this.tokenRepository = tokenRepository;
        this.uriWhiteList = uriWhiteList;
    }

    private boolean isInWhiteList(String uri){
        if(CollectionUtils.isEmpty(uriWhiteList)){
            return false;
        }

        for (String pattern:uriWhiteList) {
            if(pathMatcher.match(pattern,uri)){
                return true;
            }
        }
        return false;

    }
    @Override
    public Object run() throws ZuulException {
        RequestContext requestContext = RequestContext.getCurrentContext();
        HttpServletRequest request = requestContext.getRequest();
        String token = request.getHeader(GatewayConstants.TOKEN_KEY);
        if(StringUtils.isBlank(token)){
            authErrorSession(requestContext,token);
            return null;
        }

        JboneToken jboneToken = null;
        try {
            jboneToken = tokenRepository.getAndRefresh(token);
        } catch (Exception e) {
            logger.error("获取token失败 {}",token,e);
            authErrorSession(requestContext,token);
            return null;
        }
        if(jboneToken == null){
            logger.info("token is not found: {}",token);
            authErrorSession(requestContext,token);
            return null;
        }


        requestContext.set(GatewayConstants.TOKEN_KEY,token);
        //将用户ID保存到上下文
        requestContext.set(GatewayConstants.USER_ID,jboneToken.getUserInfo().getBaseInfo().getId());

        requestContext.addZuulRequestHeader(GatewayConstants.USER_ID,jboneToken.getUserInfo().getBaseInfo().getId() + "");
        return null;
    }

    private void authErrorSession(RequestContext requestContext,String token){
        requestContext.getResponse().setContentType("text/html;charset=UTF-8");
        requestContext.setSendZuulResponse(false);
        requestContext.setResponseStatusCode(401);
        requestContext.setResponseBody(JSON.toJSONString(Result.wrapError(401,"没有权限")));
    }
}
