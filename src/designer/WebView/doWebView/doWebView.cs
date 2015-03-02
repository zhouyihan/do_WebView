using doControlLib;
using doControlLib.Environment;
using doControlLib.tools;
using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace doUIViewDesign
{
    class doWebView : doComponentUIView
    {
        public override void DrawControl(int _x, int _y, int _width, int _height, Graphics g)
        {
            base.DrawControl(_x, _y, _width, _height, g);
            Assembly assem = this.GetType().Assembly;
            Stream stream = assem.GetManifestResourceStream("doUIViewDesign.Browsers.png");
            RectangleF _rect = new RectangleF(_x, _y, this.CurrentModel.Width, this.CurrentModel.Height);
            Image _image = Image.FromStream(stream);
            if (_image != null)
            {
                g.DrawImage(_image, _rect);
            } 
        }

        protected override bool AllowTransparent
        {
            get
            {
                return false;
            }
        }
    }
}
