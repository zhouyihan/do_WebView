using doCore.Object;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace do_WebView.extdefine
{
    public abstract class do_WebView_MAbstract : doUIModule
    {
        protected do_WebView_MAbstract()
            : base()
        {
            
        }
        /// <summary>
        /// 初始化
        /// </summary>
        public override void OnInit()
        {
            base.OnInit();
            //注册属性
        }
    }
}
