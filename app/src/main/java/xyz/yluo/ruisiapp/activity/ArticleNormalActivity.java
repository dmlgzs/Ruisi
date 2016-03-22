package xyz.yluo.ruisiapp.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cz.msebera.android.httpclient.Header;
import jp.wasabeef.recyclerview.animators.FadeInDownAnimator;
import xyz.yluo.ruisiapp.R;
import xyz.yluo.ruisiapp.adapter.ArticleRecycleAdapter;
import xyz.yluo.ruisiapp.data.SingleArticleData;
import xyz.yluo.ruisiapp.fragment.NeedLoginDialogFragment;
import xyz.yluo.ruisiapp.listener.HidingScrollListener;
import xyz.yluo.ruisiapp.listener.RecyclerViewClickListener;
import xyz.yluo.ruisiapp.utils.AsyncHttpCilentUtil;
import xyz.yluo.ruisiapp.utils.ConfigClass;
import xyz.yluo.ruisiapp.utils.GetLevel;
import xyz.yluo.ruisiapp.utils.PostHander;

/**
 * Created by free2 on 16-3-6.
 *
 */
public class ArticleNormalActivity extends AppCompatActivity
        implements RecyclerViewClickListener {

    @Bind(R.id.topic_recycler_view)
    protected RecyclerView mRecyclerView;
    @Bind(R.id.topic_refresh_layout)
    protected SwipeRefreshLayout refreshLayout;
    @Bind(R.id.toolbar)
    protected Toolbar toolbar;
    @Bind(R.id.replay_bar)
    protected LinearLayout replay_bar;
    @Bind(R.id.input_aera)
    protected EditText input_aera;
    @Bind(R.id.action_send)
    protected ImageButton action_send;
    @Bind(R.id.action_smiley)
    protected ImageButton action_smiley;
    @Bind(R.id.smiley_container)
    protected LinearLayout smiley_container;
    @Bind(R.id.topic_layout_root)
    protected CoordinatorLayout topic_layout_root;

    //当前评论第几页
    private int ARTICLE_CURRENT_PAGE = 1;
    //存储数据 需要填充的列表
    private List<SingleArticleData> mydatalist = new ArrayList<>();
    private static String ARTICLE_TID;
    private static String ARTICLE_TITLE = "";
    private static String ARTICLE_REPLY_COUNT;
    private static String ARTICLE_TYPE;
    //当前回复链接
    private String replyUrl = "";
    private ArticleRecycleAdapter mRecyleAdapter;

    //约定好要就收的数据
    public static void open(Context context, String tid,String title,String replycount,String type) {
        Intent intent = new Intent(context, ArticleNormalActivity.class);
        ARTICLE_TID = tid;
        ARTICLE_TITLE = title;
        ARTICLE_REPLY_COUNT = replycount;
        ARTICLE_TYPE = type;
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(ARTICLE_TITLE);
        }

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyleAdapter = new ArticleRecycleAdapter(this, this, mydatalist);
        mRecyclerView.setAdapter(mRecyleAdapter);

        //item 增加删除动画
        mRecyclerView.setItemAnimator(new FadeInDownAnimator());
        mRecyclerView.getItemAnimator().setAddDuration(100);
        mRecyclerView.getItemAnimator().setRemoveDuration(10);
        mRecyclerView.getItemAnimator().setChangeDuration(10);


        mRecyclerView.addOnScrollListener(new HidingScrollListener() {
            @Override
            public void onHide() {
                CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) replay_bar.getLayoutParams();
                int bottomMargin = lp.bottomMargin;
                int distanceToScroll = replay_bar.getHeight() + bottomMargin;
                replay_bar.animate().translationY(distanceToScroll).setInterpolator(new AccelerateInterpolator(5));
            }

            @Override
            public void onShow() {
                replay_bar.animate().translationY(0).setInterpolator(new AccelerateInterpolator(5));
            }
        });



        action_smiley.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(smiley_container.getVisibility()==View.VISIBLE){
                    smiley_container.setVisibility(View.GONE);
                }else{
                    smiley_container.setVisibility(View.VISIBLE);
                }

            }
        });

        input_aera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                smiley_container.setVisibility(View.GONE);
            }
        });

        refreshLayout.post(new Runnable() {
            @Override
            public void run() {
                refreshLayout.setRefreshing(true);
                getArticleData(ARTICLE_TID, 1);
            }
        });
        //下拉刷新
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                //数据填充
                getArticleData(ARTICLE_TID, ARTICLE_CURRENT_PAGE);
            }
        });

    }

    @OnClick(R.id.action_send)
    protected void action_send_click(){

        smiley_container.setVisibility(View.GONE);
        hide_ime();
        //按钮监听
        if (ConfigClass.CONFIG_ISLOGIN) {
            post_reply(input_aera.getText().toString());

        } else {
            NeedLoginDialogFragment dialogFragment = new NeedLoginDialogFragment();
            dialogFragment.show(getFragmentManager(), "needlogin");
        }


    }

    @OnClick({R.id._1000, R.id._1001,R.id._1002,R.id._1003,R.id._1005,
            R.id._1006,R.id._1007,R.id._1008,R.id._1009,R.id._1010,
            R.id._1011,R.id._1012,R.id._1013,R.id._1014,R.id._1015,
            R.id._1016,R.id._1017,R.id._1018,R.id._1019,R.id._1020,
            R.id._1021,R.id._1022,R.id._1023,R.id._1024,R.id._1025,
            R.id._1027,R.id._1028,R.id._1029,R.id._1030, R.id._998,
            R.id._999,R.id._9998,R.id._9999
    })
    protected void smiley_click(ImageButton btn){
        //插入表情
        //{:16_1021:}
        //_1021
        //input_aera.append(btn.getTag().toString());
        String tmp = btn.getTag().toString();
        PostHander hander = new PostHander(getApplicationContext(),input_aera);
        hander.insertSmiley("{:16" + tmp + ":}", btn.getDrawable());
    }

    //登陆页面返回结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode==RESULT_OK){
            String result = data.getExtras().getString("result");//得到新Activity 关闭后返回的数据
            Toast.makeText(getApplicationContext(),"result"+result,Toast.LENGTH_SHORT).show();
        }
    }

    //recyclerView item点击事件 加载更多事件
    @Override
    public void recyclerViewListClicked(View v, int position) {
        Toast.makeText(getApplicationContext(),"被电击"+position+"|"+mydatalist.size(),Toast.LENGTH_SHORT).show();
        if(position==mydatalist.size()){
            int newpage  = ARTICLE_CURRENT_PAGE;
            //加载更多 被电击
            if(position%10==0){
                //本页最后一个
                //到下一页去数据填充
                ARTICLE_CURRENT_PAGE++;
                newpage+=1;
            }
            getArticleData(ARTICLE_TID, newpage);
        }
    }


    //文章一页的html 根据页数 tid
    private void getArticleData(String tid, final int page) {

        String url = "forum.php?mod=viewthread&tid="+ARTICLE_TID+"&page="+ARTICLE_CURRENT_PAGE+"&mobile=2";

        System.out.print("\nurl"+url);
        AsyncHttpCilentUtil.get(this, url, null, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                String res = new String(responseBody);
                new DealWithArticleData(res).execute((Void) null);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Toast.makeText(getApplicationContext(), "网络错误", Toast.LENGTH_SHORT).show();
            }
        });

    }


    public class DealWithArticleData extends AsyncTask<Void,Void,String>{
        //* 传入一篇文章html
        //* 返回list<SingleArticleData>

        private String htmlData;

        //目前有多少数据
        private int start =0;

        public DealWithArticleData(String htmlData) {
            this.htmlData = htmlData;
            start = mydatalist.size()-1;
        }

        @Override
        protected String doInBackground(Void... params) {
            //title, type, replyCount, username, userUrl, userImgUrl, postTime, cotent
            String content = "未能获取数据";
            String userimg = "";
            String userurl = "";
            String username = "";
            String posttime = "";

            //list 所有楼数据
            Document doc = Jsoup.parse(htmlData);


            //获取回复/hash
            if (doc.select("input[name=formhash]").first() != null) {
                replyUrl = doc.select("form#fastpostform").attr("action");
                ConfigClass.CONFIG_FORMHASH = doc.select("input[name=formhash]").attr("value"); // 具有 formhash 属性的链接
            }
            Elements elements = doc.select(".postlist");
            if(elements!=null){
                System.out.print("\n"+elements.html()+"\n");

                SingleArticleData data;
                //获取标题
                if(ARTICLE_TITLE.equals("")){
                    ARTICLE_TITLE = elements.select("h2").first().text().trim();
                }

                Elements postlist = elements.select("div[id^=pid]");

                for(Element temp:postlist){
                    userimg = temp.select("span[class=avatar]").select("img").attr("src");
                    Elements userInfo = temp.select("ul.authi");
                    userurl = userInfo.select("a[href^=home.php?mod=space&uid=]").attr("href");
                    username = userInfo.select("a[href^=home.php?mod=space&uid=]").text();
                    posttime = userInfo.select("li.grey.rela").text();

//                    //替换贴吧表情到本地 可有可无
//                    //("static/image/smiley/tieba/","file:///android_asset/smiley/tieba/");
//                    for (Element imagetemp : temp.select("img[src^=static/image/smiley/tieba/]")) {
//                        String imgUrl = imagetemp.attr("src");
//                        String newimgurl =  imgUrl.replace("static/image/smiley/tieba/","file:///android_asset/smiley/tieba/");
//                        temp.attr("src", newimgurl);
//                    }
                    // 修正图片链接地址
                    //[attr^=value], [attr$=value], [attr*=value]这三个语法分别代表，属性以 value 开头、结尾以及包含
                    //"img[file^=http://rs.xidian.edu.cn/forum.php?mod=image],img[file^=./data/attachment/]"
                    //forum.php?mod=image&aid=853465&size=140x140&key=a10bc9320c15d379&type=fixnone
                    //http://rs.xidian.edu.cn/data/attachment/image/000/85/34/65_2000_550.jpg?mobile=2
                    for (Element tempp : temp.select("img[id^=aimg]"))
                    {
                        //aimg_850863
                        String imgid1 = tempp.attr("id");
                        tempp.attr("src", "http://rs.xidian.edu.cn/data/attachment/image/000/"+
                                imgid1.substring(5,7)+"/"+imgid1.substring(7,9)+"/"+imgid1.substring(9,11)+
                                "_2000_550.jpg?mobile=2");
                    }

                    //是否移除所有样式
                    if(ConfigClass.CONFIG_SHOW_PLAIN_TEXT){
                        //移除所有style
                        //移除font所有样式
                        temp.select("[style]").removeAttr("style");
                        temp.select("font").removeAttr("color").removeAttr("size").removeAttr("face");
                    }

                    //修改表情大小
                    for (Element tempp : temp.select("img[src^=static/image/smiley/]")) {

                        tempp.attr("style", "width:30px;height: 30px;");
                    }
                    //替换贴吧表情到本地
                    //可有无
//                    //("static/image/smiley/tieba/","file:///android_asset/smiley/tieba/");
//                    for (Element temp : element.select("img[src^=static/image/smiley/tieba/]")) {
//                        //System.out.print("replace before------>>>>>>>>>>>"+temp+"\n");
//                        String imgUrl = temp.attr("src");
//                        String newimgurl =  imgUrl.replace("static/image/smiley/tieba/","file:///android_asset/smiley/tieba/");
//                        //System.out.print("replace------>>>>>>>>>>>"+imgUrl+newimgurl+"\n");
//                        temp.attr("src", newimgurl);
//                    }

                    //替换无意义的 br
                    content = temp.select(".message").html().replaceAll("(\\s*<br>\\s*){2,}","");

                    //文章内容
                    if(mydatalist.size()==0){
                        String newtime = posttime.replace("收藏","");
                        //title, type, replyCount,username,userUrl,userImgUrl,postTime,cotent
                        data = new SingleArticleData(ARTICLE_TITLE,ARTICLE_TYPE,ARTICLE_REPLY_COUNT,username,userurl,userimg,newtime,content);
                        mydatalist.add(data);
                    }else{
                        //评论
                        //String username, String userUrl, String userImgUrl, String postTime,String cotent
                        data = new SingleArticleData(username,userurl,userimg,posttime,content);
                        mydatalist.add(data);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            //没有获取到数据
            if(start==mydatalist.size()-1){
                //没有加载到数据
                Toast.makeText(getApplicationContext(),"暂无更多",Toast.LENGTH_SHORT).show();
                ARTICLE_CURRENT_PAGE--;
            }

            mRecyleAdapter.notifyItemRangeInserted(start, mydatalist.size()-start);
            refreshLayout.setRefreshing(false);
        }

    }

    @Override
    public void onBackPressed() {
        if(smiley_container.getVisibility()==View.VISIBLE){
            smiley_container.setVisibility(View.GONE);
        }else{
            super.onBackPressed();
        }

    }

    private void post_reply(String text){
        final ProgressDialog progress;
        progress = ProgressDialog.show(this, "正在发送",
                "请等待", true);
        int len =0;
        // Toast.makeText(getApplicationContext(),text,Toast.LENGTH_SHORT).show();
        try {
            len = text.getBytes("UTF-8").length;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if(len<13){
            Toast.makeText(getApplicationContext(),"字数不够要13个字节！！",Toast.LENGTH_SHORT).show();
        }else {
            //尝试回复
            /*
            message:帮顶
            formhash:70af5bb6
            */
            RequestParams params = new RequestParams();
            params.put("formhash", ConfigClass.CONFIG_FORMHASH);
            params.put("message", text);

            //forum.php?mod=post&action=reply&fid=72&tid=841526&extra=&replysubmit=yes&mobile=2
            //forum.php?mod=post&action=reply&fid=72&tid=841526&extra=&replysubmit=yes&mobile=2&handlekey=fastpost&loc=1&inajax=1

            AsyncHttpCilentUtil.post(getApplicationContext(), replyUrl+"&handlekey=fastpost&loc=1&inajax=1", params, new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    String res = new String(responseBody);
                    if (res.contains("回复发布成功")) {
                        progress.dismiss();
                        Toast.makeText(getApplicationContext(), "回复发表成功", Toast.LENGTH_SHORT).show();
                        input_aera.setText("");
                        hide_ime();
                    } else {
                        progress.dismiss();
                        Toast.makeText(getApplicationContext(), "由于未知原因发表失败", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    progress.dismiss();
                    Toast.makeText(getApplicationContext(), "网络错误！！！", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void hide_ime(){
        // Check if no view has focus:
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}