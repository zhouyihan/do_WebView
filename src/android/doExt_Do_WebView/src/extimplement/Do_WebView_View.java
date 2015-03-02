package extimplement;

import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import core.DoServiceContainer;
import core.helper.DoUIModuleHelper;
import core.helper.jsonparse.DoJsonNode;
import core.interfaces.DoIPage;
import core.interfaces.DoIScriptEngine;
import core.interfaces.DoIUIModuleView;
import core.object.DoInvokeResult;
import core.object.DoSourceFile;
import core.object.DoUIContainer;
import core.object.DoUIModule;
import extdefine.Do_WebView_IMethod;
import extdefine.Do_WebView_MAbstract;

/**
 * 自定义扩展UIView组件实现类，此类必须继承相应VIEW类，并实现DoIUIModuleView,Do_WebView_IMethod接口；
 * #如何调用组件自定义事件？可以通过如下方法触发事件：
 * this.model.getEventCenter().fireEvent(_messageName, jsonResult);
 * 参数解释：@_messageName字符串事件名称，@jsonResult传递事件参数对象；
 * 获取DoInvokeResult对象方式new DoInvokeResult(this.model.getUniqueKey());
 */
public class Do_WebView_View extends LinearLayout implements DoIUIModuleView,Do_WebView_IMethod{
	
	private static final int PULL_TO_REFRESH = 0; // 下拉刷新
	private static final int RELEASE_TO_REFRESH = 1; // 松开后刷新
	private static final int REFRESHING = 2; // 加载中...
	private static final int PULL_DOWN_STATE = 3; // 刷新完成

	private View mHeaderView;
	private int mHeaderViewHeight;
	private int mLastMotionX, mLastMotionY;
	private int mHeaderState;
	private int mPullState;
	private boolean supportHeaderRefresh;
	private String headerViewAddress; // headerview 的地址
	private WebView webView;

	/**
	 * 每个UIview都会引用一个具体的model实例；
	 */
	private Do_WebView_MAbstract model;
	

	public Do_WebView_View(Context context) {
		super(context);
		this.setOrientation(VERTICAL);
		webView = new WebView(context);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.setVerticalScrollBarEnabled(false);
		webView.getSettings().setRenderPriority(RenderPriority.HIGH);
		webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		webView.getSettings().setDatabaseEnabled(true);
		webView.getSettings().setDomStorageEnabled(true);
		webView.getSettings().setAppCacheEnabled(true);
		webView.clearView();
		webView.getSettings().setLightTouchEnabled(true);
		webView.setWebViewClient(new WebViewClient() {
			public void onLoadResource(android.webkit.WebView view, java.lang.String url) {
				DoServiceContainer.getLogEngine().writeDebug("Load resource=" + url);
			}

			public boolean shouldOverrideUrlLoading(WebView view, String url) {

				if (url.contains("tel")) { // 拨打电话
					Uri uri = Uri.parse(url);
					Intent dial = new Intent("android.intent.action.DIAL", uri);
					((Activity) model.getCurrentPage().getPageView()).startActivity(dial);
					return true;
				}

				if (url.contains("mailto")) { // 发送邮件
					Uri uri = Uri.parse(url);
					Intent email = new Intent("android.intent.action.SENDTO", uri);
					((Activity) model.getCurrentPage().getPageView()).startActivity(email);
					return true;
				}

				// 重写此方法表明点击网页里面的链接还是在当前的webview里跳转，不跳到浏览器那边
				try {
					view.loadUrl(url);
				} catch (Exception e) {
					DoServiceContainer.getLogEngine().writeError("DoWebViewView : shouldOverrideUrlLoading\n", e);
				}
				return true;
			}

			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				DoServiceContainer.getLogEngine().writeError("执行Web脚本错误", new Exception(failingUrl + " 发生" + errorCode + "错误:" + description));
			}

			public void onPageFinished(WebView view, String url) {
				super.onPageFinished(view, url);
				webBrowser_View_DocumentCompleted(url);
				Do_WebView_View.this.requestFocus(View.FOCUS_UP | View.FOCUS_DOWN);
			}

			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				super.onPageStarted(view, url, favicon);
				webBrowser_View_DocumentStart(url);
			}
		});

		// 设置浏览器相应js的alert function和confirm function
		webView.setWebChromeClient(new WebChromeClient() {
			public boolean onJsAlert(WebView view, String url, String message, final android.webkit.JsResult result) {
				AlertDialog.Builder builder = new AlertDialog.Builder((Activity) model.getCurrentPage().getPageView());
				builder.setMessage(message);
				builder.setPositiveButton("OK", new AlertDialog.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						result.confirm();
					}
				});
				builder.setCancelable(false);
				builder.create();
				builder.show();
				return true;
			}

			public boolean onJsConfirm(WebView view, String url, String message, final android.webkit.JsResult result) {
				AlertDialog.Builder builder = new AlertDialog.Builder((Activity) model.getCurrentPage().getPageView());
				builder.setMessage(message);
				builder.setPositiveButton("OK", new AlertDialog.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						result.confirm();
					}
				});
				builder.setNeutralButton("Cancel", new AlertDialog.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						result.cancel();
					}
				});
				builder.setCancelable(false);
				builder.create();
				builder.show();
				return true;
			}

			public void onProgressChanged(WebView view, int newProgress) {
				if (model.getCurrentPage().getPageView() instanceof Activity)
					((Activity) model.getCurrentPage().getPageView()).setProgress(newProgress * 100);
			}

			public void onConsoleMessage(String message, int lineNumber, String sourceID) {
				DoServiceContainer.getLogEngine().writeError("执行错误", new Exception(sourceID + "-line" + lineNumber + ":" + message));
			}
		});
	}
	
	/**
	 * 初始化加载view准备,_doUIModule是对应当前UIView的model实例
	 */
	@Override
	public void loadView(DoUIModule _doUIModule) throws Exception {
		this.model = (Do_WebView_MAbstract)_doUIModule;
		webView.addJavascriptInterface(this.model.getCurrentPage().getScriptEngine(), "external");

		String _headerViewPath = this.model.getHeaderView();
		if (_headerViewPath != null && !"".equals(_headerViewPath.trim())) {
			try {
				DoIPage _doPage = this.model.getCurrentPage();
				DoSourceFile _uiFile = _doPage.getCurrentApp().getSourceFS().getSourceByFileName(_headerViewPath);
				if (_uiFile != null) {

					DoUIContainer _rootUIContainer = new DoUIContainer(_doPage);
					_rootUIContainer.loadFromFile(_uiFile, null, null);
					DoUIModule _model = _rootUIContainer.getRootView();
					headerViewAddress = _model.getUniqueKey();

					View _headerView = (View) _model.getCurrentUIModuleView();
					// 设置headerView 的 宽高
					_headerView.setLayoutParams(new LayoutParams((int) _model.getRealWidth(), (int) _model.getRealHeight()));
					addHeaderView(_headerView);
					this.supportHeaderRefresh = true;
				} else {
					this.supportHeaderRefresh = false;
					DoServiceContainer.getLogEngine().writeDebug("试图打开一个无效的页面文件:" + _headerViewPath);
				}
			} catch (Exception _err) {
				DoServiceContainer.getLogEngine().writeError("DoWebView  headerView \n", _err);
			}
		}
		webView.setBackgroundColor(Color.TRANSPARENT);
		this.addView((View) webView, new LinearLayout.LayoutParams(-1, -1));
	}
	
	private void addHeaderView(View _mHeaderView) {
		// header view
		this.mHeaderView = _mHeaderView;
		// header layout
		DoUIModuleHelper.measureView(mHeaderView);
		mHeaderViewHeight = mHeaderView.getMeasuredHeight();
		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, mHeaderViewHeight);
		// 设置topMargin的值为负的header View高度,即将其隐藏在最上方
		params.topMargin = -(mHeaderViewHeight);
		addView(mHeaderView, params);
	}
	
	/**
	 * 动态修改属性值时会被调用，方法返回值为true表示赋值有效，并执行onPropertiesChanged，否则不进行赋值；
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public boolean onPropertiesChanging(Map<String, String> _changedValues) {
		return true;
	}
	
	/**
	 * 属性赋值成功后被调用，可以根据组件定义相关属性值修改UIView可视化操作；
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public void onPropertiesChanged(Map<String, String> _changedValues) {
		DoUIModuleHelper.handleBasicViewProperChanged(this.model, _changedValues);
		if (_changedValues.containsKey("url")) {
			String _fullUrl = _changedValues.get("url");
			this.Navigate(_fullUrl);
		}
	}
	
	/**
	 * 同步方法，JS脚本调用该组件对象方法时会被调用，可以根据_methodName调用相应的接口实现方法；
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V）
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public boolean invokeSyncMethod(String _methodName, DoJsonNode _dictParas,
			DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult)throws Exception {
		if ("back".equals(_methodName)) { // 回退
			back(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("forward".equals(_methodName)) { // 前进
			forward(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("reload".equals(_methodName)) { // 重新加载
			reload(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("stop".equals(_methodName)) { // 停止刷新
			stop(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("canForward".equals(_methodName)) { // 是否可继续前进
			canForward(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("canBack".equals(_methodName)) { // 是否可后退
			canBack(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("getHeaderView".equals(_methodName)) {
			getHeaderView(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("rebound".equals(_methodName)) {
			rebound(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		return false;
	}
	
	/**
	 * 异步方法（通常都处理些耗时操作，避免UI线程阻塞），JS脚本调用该组件对象方法时会被调用，
	 * 可以根据_methodName调用相应的接口实现方法；
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V）
	 * @_scriptEngine 当前page JS上下文环境
	 * @_callbackFuncName 回调函数名
	 * #如何执行异步方法回调？可以通过如下方法：
	 *	_scriptEngine.callback(_callbackFuncName, _invokeResult);
	 * 参数解释：@_callbackFuncName回调函数名，@_invokeResult传递回调函数参数对象；
	   获取DoInvokeResult对象方式new DoInvokeResult(this.model.getUniqueKey());
	 */
	@Override
	public boolean invokeAsyncMethod(String _methodName, DoJsonNode _dictParas,
			DoIScriptEngine _scriptEngine, String _callbackFuncName) {
		if ("loadString".equals(_methodName)) { // 加载html字符串
			try {
				loadString(_dictParas, _scriptEngine, _callbackFuncName);
				return true;
			} catch (Exception e) {
				DoServiceContainer.getLogEngine().writeError("执行loadString错误", e);
			}
		}
		return false;
	}
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		int y = (int) e.getRawY();
		int x = (int) e.getRawX();
		switch (e.getAction()) {
		case MotionEvent.ACTION_DOWN:
			// 首先拦截down事件,记录y坐标
			mLastMotionY = y;
			mLastMotionX = x;
			break;
		case MotionEvent.ACTION_MOVE:
			// deltaY > 0 是向下运动,< 0是向上运动
			int deltaX = x - mLastMotionX;
			int deltaY = y - mLastMotionY;
			boolean isRefresh = isRefreshViewScroll(deltaX, deltaY);
			// 一旦底层View收到touch的action后调用这个方法那么父层View就不会再调用onInterceptTouchEvent了，也无法截获以后的action
			getParent().requestDisallowInterceptTouchEvent(isRefresh);
			if (isRefresh) {
				return true;
			}
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			break;
		}
		return false;
	}

	/**
	 * 是否应该到了父View,即PullToRefreshView滑动
	 * 
	 * @param deltaY
	 *            , deltaY > 0 是向下运动,< 0是向上运动
	 * @return
	 */
	private boolean isRefreshViewScroll(int deltaX, int deltaY) {
		if (mHeaderState == REFRESHING) {
			return false;
		}
		// 对于ScrollView
		if (webView != null) {
			// 子scroll view滑动到最顶端
			if (deltaY > 0 && supportHeaderRefresh && webView.getScrollY() == 0) {
				mPullState = PULL_DOWN_STATE;
				// 刷新完成......
				return true;
			}
		}
		return false;
	}

	/*
	 * 如果在onInterceptTouchEvent()方法中没有拦截(即onInterceptTouchEvent()方法中 return
	 * false)则由PullToRefreshView 的子View来处理;否则由下面的方法来处理(即由PullToRefreshView自己来处理)
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int y = (int) event.getRawY();
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			// onInterceptTouchEvent已经记录
			// mLastMotionY = y;
			break;
		case MotionEvent.ACTION_MOVE:
			int deltaY = y - mLastMotionY;
			if (mPullState == PULL_DOWN_STATE) {// 执行下拉
				if (supportHeaderRefresh)
					headerPrepareToRefresh(deltaY);
				// setHeaderPadding(-mHeaderViewHeight);
			}
			mLastMotionY = y;
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			int topMargin = getHeaderTopMargin();
			if (mPullState == PULL_DOWN_STATE) {
				if (topMargin >= 0) {
					// 开始刷新
					if (supportHeaderRefresh)
						headerRefreshing();
				} else {
					if (supportHeaderRefresh)
						// 还没有执行刷新，重新隐藏
						setHeaderTopMargin(-mHeaderViewHeight);
				}
			}
			break;
		}
		return false;
	}

	/**
	 * 获取当前header view 的topMargin
	 * 
	 */
	private int getHeaderTopMargin() {
		LayoutParams params = (LayoutParams) mHeaderView.getLayoutParams();
		return params.topMargin;
	}

	/**
	 * header refreshing
	 * 
	 */
	private void headerRefreshing() {
		mHeaderState = REFRESHING;
		setHeaderTopMargin(0);
		doPullRefresh(mHeaderState, 0);
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				onHeaderRefreshComplete();
			}
		}, 3000);

	}

	/**
	 * 设置header view 的topMargin的值
	 * @param topMargin，为0时，说明header view 刚好完全显示出来； 为-mHeaderViewHeight时，说明完全隐藏了
	 */
	private void setHeaderTopMargin(int topMargin) {
		LayoutParams params = (LayoutParams) mHeaderView.getLayoutParams();
		params.topMargin = topMargin;
		mHeaderView.setLayoutParams(params);
		invalidate();
	}

	/**
	 * header view 完成更新后恢复初始状态
	 * 
	 */
	public void onHeaderRefreshComplete() {
		setHeaderTopMargin(-mHeaderViewHeight);
		mHeaderState = PULL_TO_REFRESH;
	}

	/**
	 * header 准备刷新,手指移动过程,还没有释放
	 * 
	 * @param deltaY
	 *            ,手指滑动的距离
	 */
	private void headerPrepareToRefresh(int deltaY) {
		int newTopMargin = changingHeaderViewTopMargin(deltaY);
		// 当header view的topMargin>=0时，说明已经完全显示出来了,修改header view 的提示状态
		if (newTopMargin >= 0 && mHeaderState != RELEASE_TO_REFRESH) {
			mHeaderState = RELEASE_TO_REFRESH;
		} else if (newTopMargin < 0 && newTopMargin > -mHeaderViewHeight) {// 拖动时没有释放
			mHeaderState = PULL_TO_REFRESH;
		}
		doPullRefresh(mHeaderState, newTopMargin);
	}

	/**
	 * 修改Header view top margin的值
	 * 
	 * @param deltaY
	 */
	private int changingHeaderViewTopMargin(int deltaY) {
		LayoutParams params = (LayoutParams) mHeaderView.getLayoutParams();
		float newTopMargin = params.topMargin + deltaY * 0.3f;
		// 这里对上拉做一下限制,因为当前上拉后然后不释放手指直接下拉,会把下拉刷新给触发了
		// 表示如果是在上拉后一段距离,然后直接下拉
//		if (deltaY > 0 && mPullState == PULL_UP_STATE && Math.abs(params.topMargin) <= mHeaderViewHeight) {
//			return params.topMargin;
//		}
		// 同样地,对下拉做一下限制,避免出现跟上拉操作时一样的bug
		if (deltaY < 0 && mPullState == PULL_DOWN_STATE && Math.abs(params.topMargin) >= mHeaderViewHeight) {
			return params.topMargin;
		}
		params.topMargin = (int) newTopMargin;
		mHeaderView.setLayoutParams(params);
		invalidate();
		return params.topMargin;
	}
	
	/**
	* 释放资源处理，前端JS脚本调用closePage或执行removeui时会被调用；
	*/
	@Override
	public void onDispose() {
		//...do something
	}
	
	/**
	* 重绘组件，构造组件时由系统框架自动调用；
	  或者由前端JS脚本调用组件onRedraw方法时被调用（注：通常是需要动态改变组件（X、Y、Width、Height）属性时手动调用）
	*/
	@Override
	public void onRedraw() {
		this.setLayoutParams(DoUIModuleHelper.getLayoutParams(this.model));
	}
	
	/**
	 * 获取当前model实例
	 */
	@Override
	public DoUIModule getModel() {
		return model;
	}
	
	private void webBrowser_View_DocumentCompleted(String url) {
		try {
			onLoaded();
		} catch (Exception e) {
			DoServiceContainer.getLogEngine().writeError("调用loaded错误", e);
		}
	}

	private void webBrowser_View_DocumentStart(String url) {
		try {
			onStart();
		} catch (Exception e) {
			DoServiceContainer.getLogEngine().writeError("调用start错误", e);
		}
	}
	
	private void onLoaded() {
		DoInvokeResult _invokeResult = new DoInvokeResult(this.model.getUniqueKey());
		this.model.getEventCenter().fireEvent("loaded", _invokeResult);
	}

	private void onStart() {
		DoInvokeResult _invokeResult = new DoInvokeResult(this.model.getUniqueKey());
		this.model.getEventCenter().fireEvent("start", _invokeResult);
	}
	
	private void Navigate(String _fullUrl) {
		if (_fullUrl.startsWith("http:") || _fullUrl.startsWith("https:") || _fullUrl.startsWith("file:")) {
			webView.loadUrl(_fullUrl);
		} else {
			webView.loadUrl("file://" + _fullUrl);
		}
	}
	
	private void doPullRefresh(int mHeaderState, int newTopMargin) {
		DoInvokeResult _invokeResult = new DoInvokeResult(this.model.getUniqueKey());
		try {
			DoJsonNode _node = new DoJsonNode();
			_node.setOneInteger("state", mHeaderState);
			_node.setOneText("y", (newTopMargin / this.model.getYZoom()) + "");
			_invokeResult.setResultNode(_node);
			this.model.getEventCenter().fireEvent("pull", _invokeResult);
		} catch (Exception _err) {
			DoServiceContainer.getLogEngine().writeError("DoWebview pull \n", _err);
		}
	}

	@Override
	public void back(DoJsonNode _dictParas, DoIScriptEngine _scriptEngine,
			DoInvokeResult _invokeResult) throws Exception {
		webView.goBack();
		model.setPropertyValue("url", webView.getUrl());
	}

	@Override
	public void forward(DoJsonNode _dictParas, DoIScriptEngine _scriptEngine,
			DoInvokeResult _invokeResult) throws Exception {
		webView.goForward();
		model.setPropertyValue("url", webView.getUrl());
	}

	@Override
	public void reload(DoJsonNode _dictParas, DoIScriptEngine _scriptEngine,
			DoInvokeResult _invokeResult) throws Exception {
		webView.reload();
	}

	@Override
	public void stop(DoJsonNode _dictParas, DoIScriptEngine _scriptEngine,
			DoInvokeResult _invokeResult) throws Exception {
		webView.stopLoading();
	}

	@Override
	public void canForward(DoJsonNode _dictParas,
			DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult)
			throws Exception {
		webView.canGoForward();
	}

	@Override
	public void canBack(DoJsonNode _dictParas, DoIScriptEngine _scriptEngine,
			DoInvokeResult _invokeResult) throws Exception {
		webView.canGoBack();
	}

	@Override
	public void getHeaderView(DoJsonNode _dictParas,
			DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult)
			throws Exception {
		_invokeResult.setResultText(headerViewAddress);
		
	}

	@Override
	public void rebound(DoJsonNode _dictParas, DoIScriptEngine _scriptEngine,
			DoInvokeResult _invokeResult) throws Exception {
		onHeaderRefreshComplete();
	}

	@Override
	public void loadString(DoJsonNode _dictParas,
			DoIScriptEngine _scriptEngine, String _callbackFuncName)
			throws Exception {
		String htmlStr = _dictParas.getOneText("text", "");
		webView.loadData(htmlStr, "text/html; charset=UTF-8", null);// 这种写法可以正确解码
	}
	
	
}
