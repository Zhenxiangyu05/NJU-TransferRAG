package com.yu.transferrag.conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Offline regression of the acceptance harness; only loopback HTTP, synthetic authentication and data. */
public final class ConversationAcceptanceStub {
    public static void main(String[] args) throws Exception {
        var json=new ObjectMapper();var pool=Executors.newFixedThreadPool(4);
        var school=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var official=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        for(var server:List.of(school,official)) {
            server.setExecutor(pool);server.createContext("/",exchange->{
                try {
                    var body=json.readTree(exchange.getRequestBody());Map<String,Object> output;
                    if(exchange.getRequestURI().getPath().endsWith("/embeddings")) {
                        int count=body.path("input").isArray()?body.path("input").size():1;
                        var data=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)data.add(Map.of("object","embedding","index",i,"embedding",Collections.nCopies(1024,.25)));
                        output=Map.of("object","list","model","bge-m3","data",data,"usage",Map.of("prompt_tokens",1,"total_tokens",1));
                    } else {
                        String system=body.path("messages").get(0).path("content").asText();
                        String user=body.path("messages").get(1).path("content").asText();String answer;
                        if(system.contains("追问语义补全")) {
                            var current=json.readTree(user).path("current").asText();
                            answer=json.writeValueAsString(Map.of("status","RESOLVED","question",current.contains("计算机")?"南京大学转计算机科学与技术专业的考试考什么？":"南京大学转软件工程的考试考什么？"));
                        } else if(user.contains("evidenceCitationIds")) {
                            String id="S1";if(user.startsWith("用户问题：\n本地测试资料中，南京大学图书馆")) {
                                var match=Pattern.compile("\\[(S\\d+)\\]\\n(?:(?!\\[S\\d+\\]).)*?content:\\n[^\\n]*图书馆",Pattern.DOTALL).matcher(user);if(match.find())id=match.group(1);
                            }
                            answer=json.writeValueAsString(Map.of("answerable",true,"evidenceCitationIds",List.of(id),"reason","synthetic"));
                        } else if(user.startsWith("用户问题：\n本地测试资料中，南京大学图书馆")) {
                            var marker=Pattern.compile("\\[(S\\d+)\\]").matcher(user);String citation=marker.find()?marker.group(1):"S1";
                            answer="根据个人测试资料，周末9点至下午5点开放。["+citation+"]";
                        }
                        else if(user.contains("当前原始问题：我刚才说错了"))answer="根据个人测试资料，考试为离散数学和算法设计。[S1]";
                        else answer="根据个人测试资料，大一学生需完成高等数学，考试为Java基础和程序设计。[S1]";
                        output=Map.of("id","synthetic","object","chat.completion","created",1,"model",body.path("model").asText(),"choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",answer),"finish_reason","stop")),"usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2));
                    }
                    byte[] bytes=json.writeValueAsBytes(output);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
                } catch(Exception failure){exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}
            });server.start();
        }
        try {
            var env=Map.of("AI_BASE_URL","http://127.0.0.1:"+school.getAddress().getPort()+"/v1","AI_API_KEY","synthetic-school","AI_EMBEDDING_MODEL","bge-m3","AI_CHAT_PROVIDER","deepseek-official","DEEPSEEK_API_KEY","synthetic-official","DEEPSEEK_CHAT_MODEL","deepseek-flash","APP_AI_CHAT_DEEPSEEK_BASE_URL","http://127.0.0.1:"+official.getAddress().getPort());
            var child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("java.class.path"),"com.yu.transferrag.conversation.ConversationLiveAcceptance","--offline");
            child.environment().keySet().removeIf(k->k.matches("^(AI_|DEEPSEEK_|SPRING_AI_|OPENAI_|APP_AI_).*$"));
            var process=child.start();var errors=CompletableFuture.runAsync(()->{try{process.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream());}catch(Exception ignored){}});
            process.getOutputStream().write(json.writeValueAsBytes(env));process.getOutputStream().close();
            try(var lines=new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))){String line;while((line=lines.readLine())!=null)if(line.startsWith("SAFE_")||line.startsWith("LIVE_ACCEPTANCE_"))System.out.println(line);}
            int exit=process.waitFor();errors.join();if(exit!=0)throw new IllegalStateException("OFFLINE_HARNESS_FAILED");
        } finally {school.stop(0);official.stop(0);pool.shutdownNow();}
    }
}
