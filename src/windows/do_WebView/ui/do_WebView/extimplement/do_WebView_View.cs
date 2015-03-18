using doCore.Helper;
using doCore.Helper.JsonParse;
using doCore.Interface;
using doCore.Object;
using do_WebView.extdefine;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;
using System.IO;
using Windows.Storage;
using Windows.Storage.Streams;
using Windows.UI;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Media;
using Windows.UI.Xaml.Media.Imaging;
using Windows.UI.Text;
using Windows.UI.Popups;

namespace do_WebView.extimplement
{
    /// <summary>
    /// 自定义扩展UIView组件实现类，此类必须继承相应控件类或UserControl类，并实现doIUIModuleView,@TYPEID_IMethod接口；
    /// #如何调用组件自定义事件？可以通过如下方法触发事件：
    /// this.model.EventCenter.fireEvent(_messageName, jsonResult);
    /// 参数解释：@_messageName字符串事件名称，@jsonResult传递事件参数对象；
    /// 获取doInvokeResult对象方式new doInvokeResult(model.UniqueKey);
    /// </summary>
    public class do_WebView_View : UserControl, doIUIModuleView
    {
        /// <summary>
        /// 每个UIview都会引用一个具体的model实例；
        /// </summary>
        private do_WebView_MAbstract model;
        WebView web = new WebView();
        public do_WebView_View()
        {
            this.Content = web;
        }
        /// <summary>
        /// 初始化加载view准备,_doUIModule是对应当前UIView的model实例
        /// </summary>
        /// <param name="_doComponentUI"></param>
        public void LoadView(doUIModule _doUIModule)
        {
            this.model = (do_WebView_MAbstract)_doUIModule;
            this.HorizontalAlignment = Windows.UI.Xaml.HorizontalAlignment.Left;
            this.VerticalAlignment = Windows.UI.Xaml.VerticalAlignment.Top;
            web.NavigationFailed += web_NavigationFailed;
            web.LoadCompleted += web_LoadCompleted; ;
        }

        void web_LoadCompleted(object sender, Windows.UI.Xaml.Navigation.NavigationEventArgs e)
        {
            OnWebViewStart();
            OnWebViewLoaded();
        }

        public void OnWebViewLoaded()
        {
            doInvokeResult _invokeResult = new doInvokeResult(this.model.UniqueKey);
            this.model.EventCenter.FireEvent("onloaded", _invokeResult);
        }
        public void OnWebViewStart()
        {
            doInvokeResult _invokeResult = new doInvokeResult(this.model.UniqueKey);
            this.model.EventCenter.FireEvent("onstart", _invokeResult);
        }
        async void web_NavigationFailed(object sender, WebViewNavigationFailedEventArgs e)
        {
             await new MessageDialog(e.WebErrorStatus.ToString()).ShowAsync();
        }

        public doUIModule GetModel()
        {
            return this.model;
        }

        /// <summary>
        /// 动态修改属性值时会被调用，方法返回值为true表示赋值有效，并执行OnPropertiesChanged，否则不进行赋值；
        /// </summary>
        /// <param name="_changedValues">属性集（key名称、value值）</param>
        /// <returns></returns>
        public bool OnPropertiesChanging(Dictionary<string, string> _changedValues)
        {
            return true;
        }
        /// <summary>
        /// 属性赋值成功后被调用，可以根据组件定义相关属性值修改UIView可视化操作；
        /// </summary>
        /// <param name="_changedValues">属性集（key名称、value值）</param>
        public  void OnPropertiesChanged(Dictionary<string, string> _changedValues)
        {
            doUIModuleHelper.HandleBasicViewProperChanged(this.model, _changedValues);
            if (_changedValues.Keys.Contains("url"))
            {
                web.Navigate(new Uri(_changedValues["url"], UriKind.Absolute));
            }
        }

        public bool InvokeSyncMethod(string _methodName, doJsonNode _dictParas, doIScriptEngine _scriptEngine, doInvokeResult _invokeResult)
        {
            switch (_methodName)
            {
                case "back":
                    web.GoBack();
                    this.model.SetPropertyValue("url", web.Source.ToString());
                    return true;
                case "forward":
                    web.GoForward();
                    this.model.SetPropertyValue("url", web.Source.ToString());
                    return true;
                case "reload":
                    web.Refresh();
                    return true;
                case "stop":
                    web.Stop();
                    return true;
                case "canForward":
                    return web.CanGoForward;
                case "canBack":
                    return web.CanGoBack;
            }
            return false;
        }

        public bool InvokeAsyncMethod(string _methodName, doJsonNode _dictParas, doIScriptEngine _scriptEngine, string _callbackFuncName)
        {
            switch (_methodName)
            {
                case "loadString":
                    this.web.NavigateToString(_dictParas.GetOneText("text", ""));
                    return true;
            }
            return false;
        }
        /// <summary>
        /// 重绘组件，构造组件时由系统框架自动调用；
        /// 或者由前端JS脚本调用组件onRedraw方法时被调用（注：通常是需要动态改变组件（X、Y、Width、Height）属性时手动调用）
        /// </summary>
        public void OnRedraw()
        {
            var tp = doUIModuleHelper.GetThickness(this.model);
            this.Margin = tp.Item1;
            this.Width = tp.Item2;
            this.Height = tp.Item3;
        }
        /// <summary>
        /// 释放资源处理，前端JS脚本调用closePage或执行removeui时会被调用；
        /// </summary>
        public void OnDispose()
        {

        }
    }
}
