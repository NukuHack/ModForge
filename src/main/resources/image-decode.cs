using System.Configuration;
using System.Data;
using System.IO;
using System.Windows;
using DirectXTexNet;

namespace KCDTextureExporter
{
    public partial class App : Application
    {
        private void OnStartup(object sender, StartupEventArgs e)
        {
            string[] args = e.Args;
            //Check for args being passed first.
            if (args.Contains("--input") && args.Contains("--output"))
            {
                string inputPath = GetArgValue(args, "--input");
                string outputPath = GetArgValue(args, "--output");

                bool saveRaw = args.Contains("--saveRaw");
                bool separateGloss = args.Contains("--separateGloss");
                bool deleteSrc = args.Contains("--deleteSource");
                bool recursive = args.Contains("--recursive");

                bool isOutputFolder = !outputPath.EndsWith(".tif", StringComparison.InvariantCultureIgnoreCase);

                TexHelper.LoadInstance();

                if (Directory.Exists(inputPath))
                {
                    var ddsFiles = Directory.EnumerateFiles(inputPath, "*.dds", recursive ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly);

                    foreach (var file in ddsFiles)
                    {
                        ImageConverter.ConvertImage(file, saveRaw, separateGloss, outputPath, deleteSrc, true);
                    }
                }
                else
                {
                    ImageConverter.ConvertImage(inputPath, saveRaw, separateGloss, outputPath, deleteSrc, isOutputFolder);
                }

                Shutdown();
                return;
            }

            // Launch UI if no CLI args
            new MainWindow().Show();
        }

        private static string GetArgValue(string[] args, string key)
        {
            int index = Array.IndexOf(args, key);
            if (index >= 0 && index < args.Length - 1)
                return args[index + 1];

            return "";
        }
    }
}

﻿using System.IO;

namespace KCDTextureExporter.DDS
{
    public class DDSFile
    {
        public static uint Magic = 0x20534444;
        public bool TrimmedMagic = false;
        public Header Header { get; set; } = new();
        public byte[]? Data { get; set; }
        public DDSFile(bool _trimmedMagic)
        {
            TrimmedMagic = _trimmedMagic;
        }

        public DDSFile(string fileName, bool _trimmedMagic)
        {
            TrimmedMagic = _trimmedMagic;
            Read(fileName);
        }

        public DDSFile(Stream stream, bool _trimmedMagic)
        {
            TrimmedMagic = _trimmedMagic;
            Read(stream);
        }

        public DDSFile(BinaryReader br, bool _trimmedMagic)
        {
            TrimmedMagic = _trimmedMagic;
            Read(br);
        }

        public void Read(string fileName)
        {
            using (MemoryStream ms = new(File.ReadAllBytes(fileName)))
            {
                Read(ms);
            }
        }

        public void Read(Stream stream)
        {
            using (BinaryReader br = new(stream))
            {
                Read(br);
            }
        }

        public void Read(BinaryReader br)
        {
            if (!TrimmedMagic)
            {
                uint _magic = br.ReadUInt32();

                if (_magic != Magic)
                {
                    throw new Exception("Not a DDS file.");
                }
            }

            Header = new(br);
            Data = br.ReadBytes((int)(br.BaseStream.Length - br.BaseStream.Position));
        }

        public void Write(string fileName)
        {
            using (MemoryStream ms = new())
            {
                Write(ms);

                File.WriteAllBytes(fileName, ms.ToArray());
            }
        }

        public void Write(Stream stream)
        {
            using (BinaryWriter bw = new(stream))
            {
                Write(bw);
            }
        }

        public byte[] Write()
        {
            using (MemoryStream ms = new())
            {
                Write(ms);

                return ms.ToArray();
            }
        }

        public void Write(BinaryWriter bw)
        {
            bw.Write(Magic);
            Header.Write(bw);
            bw.Write(Data!);
        }
    }
}

﻿using System.IO;
using DirectXTexNet;

namespace KCDTextureExporter.DDS
{
    public class ExtendedHeader
    {
        public DXGI_FORMAT dxgiFormat { get; set; } = DXGI_FORMAT.UNKNOWN;
        public uint resourceDimension { get; set; }
        public uint miscFlag { get; set; }
        public uint arraySize { get; set; }
        public uint miscFlags2 { get; set; }
        public ExtendedHeader()
        {

        }

        public ExtendedHeader(BinaryReader br)
        {
            Read(br);
        }

        public void Read(BinaryReader br)
        {
            dxgiFormat = (DXGI_FORMAT)br.ReadInt32();
            resourceDimension = br.ReadUInt32();
            miscFlag = br.ReadUInt32();
            arraySize = br.ReadUInt32();
            miscFlags2 = br.ReadUInt32();
        }

        public void Write(BinaryWriter bw)
        {
            bw.Write((int)dxgiFormat);
            bw.Write(resourceDimension);
            bw.Write(miscFlag);
            bw.Write(arraySize);
            bw.Write(miscFlags2);
        }
    }
}

﻿using Microsoft.Win32;
using System.Configuration;
using System.IO;
using System.Numerics;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Xml.Linq;
using System.Xml;
using System.Windows.Interop;
using KCDTextureExporter;
using DirectXTexNet;

namespace KCDTextureExporter
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool FlashWindowEx(ref FLASHWINFO pwfi);

        [StructLayout(LayoutKind.Sequential)]
        private struct FLASHWINFO
        {
            public uint cbSize;
            public IntPtr hwnd;
            public uint dwFlags;
            public uint uCount;
            public uint dwTimeout;
        }

        private const uint FLASHW_ALL = 0x3;
        private const uint FLASHW_TIMERNOFG = 0xC;

        public static void ConvertImageStatic(string filePath, bool saveRawDDS, bool separateGlossMap, string outputPath, bool deleteSourceFiles, bool isOutputFolder)
        {
            ImageConverter.ConvertImage(filePath, saveRawDDS, separateGlossMap, outputPath, deleteSourceFiles, isOutputFolder);
        }

        public MainWindow()
        {
            InitializeComponent();

            TexHelper.LoadInstance();

            ReadSettingsFile();
        }

        private async void Button_Convert_Click(object sender, RoutedEventArgs e)
        {
            string inputPath = TextBox_Input.Text.Trim();
            bool isRecursive = CheckBox_Recursive.IsChecked == true;
            bool isFolder = Directory.Exists(inputPath);
            bool isFileDDS = File.Exists(inputPath) && inputPath.EndsWith(".dds", StringComparison.OrdinalIgnoreCase);

            if (!isFolder && !isFileDDS)
                throw new Exception("Input path invalid or no .dds files found.");

            // Non-recursive, check DDS exists first.
            if (isFolder && !isRecursive)
            {
                if (!Directory.EnumerateFiles(inputPath, "*.dds", SearchOption.TopDirectoryOnly).Any())
                    throw new Exception("No .dds files found in the input folder.");
            }

            Button_Convert.IsEnabled = false;

            if (isFolder)
            {
                var tasks = BatchProcessFiles( inputPath, TextBox_Output.Text, CheckBox_SaveRawDDS.IsChecked == true, CheckBox_SeparateGlossMap.IsChecked == true, CheckBox_DeleteSourceFiles.IsChecked == true, isRecursive);

                if (!tasks.Any())
                    throw new Exception("No .dds files found in the input folder or its subfolders.");

                await Task.WhenAll(tasks);
            }
            else // single file
            {
                ConvertImageStatic( inputPath, CheckBox_SaveRawDDS.IsChecked == true, CheckBox_SeparateGlossMap.IsChecked == true, TextBox_Output.Text, CheckBox_DeleteSourceFiles.IsChecked == true, !TextBox_Output.Text.EndsWith(".tif", StringComparison.OrdinalIgnoreCase));
            }

            FlashWindow();
            Button_Convert.IsEnabled = true;
        }

        public List<Task> BatchProcessFiles(string inputFolder, string outputFolder, bool saveRawDDS, bool separateGlossMap, bool deleteSourceFiles, bool recursive)
        {
            var option = recursive
                ? SearchOption.AllDirectories
                : SearchOption.TopDirectoryOnly;

            // Find all .dds files including sub folders
            var ddsFiles = Directory.EnumerateFiles(inputFolder, "*.dds", option).ToList();

            if (!ddsFiles.Any())
                throw new Exception("No .dds files were found in the selected input folder (or its subfolders).");

            var tasks = new List<Task>();

            foreach (var file in ddsFiles)
            {
                if (recursive)
                {
                    // Compute the sub-path under inputFolder
                    var subDir = Path.GetRelativePath(
                        inputFolder,
                        Path.GetDirectoryName(file) ?? string.Empty);

                    // Build the matching folder under outputFolder
                    var destDir = Path.Combine(outputFolder, subDir);
                    Directory.CreateDirectory(destDir);

                    tasks.Add(Task.Run(() =>
                        ConvertImageStatic(file, saveRawDDS, separateGlossMap, destDir, deleteSourceFiles,true)
                    ));
                }
                else
                {
                    // Non-recursive
                    tasks.Add(Task.Run(() =>
                        ConvertImageStatic(file, saveRawDDS, separateGlossMap, outputFolder, deleteSourceFiles, true)
                    ));
                }
            }

            return tasks;
        }


        private void Button_InputPicker_Click(object sender, RoutedEventArgs e)
        {
            OpenFolderDialog dialog = new OpenFolderDialog();
            dialog.Multiselect = false;
            dialog.Title = "Input folder";

            if ((bool)dialog.ShowDialog()!)
            {
                TextBox_Input.Text = dialog.FolderName;
            }
        }

        private void Button_OutputPicker_Click(object sender, RoutedEventArgs e)
        {
            OpenFolderDialog dialog = new OpenFolderDialog();
            dialog.Multiselect = false;
            dialog.Title = "Output folder";

            if ((bool)dialog.ShowDialog()!)
            {
                TextBox_Output.Text = dialog.FolderName;
            }
        }

        private void TextBox_Drop(object sender, DragEventArgs e)
        {
            if (e.Data.GetDataPresent(DataFormats.FileDrop))
            {
                string[] files = (string[])e.Data.GetData(DataFormats.FileDrop);

                if (files.Length > 0)
                {
                    TextBox? textBox = sender as TextBox;

                    textBox!.Text = files[0];
                }
            }
        }

        private void TextBox_PreviewDragOver(object sender, DragEventArgs e) => e.Handled = true;

        private void Window_Closing(object sender, System.ComponentModel.CancelEventArgs e) => WriteSettingsFile();

        private void FlashWindow()
        {
            WindowInteropHelper wih = new(this);

            FLASHWINFO fwi = new FLASHWINFO
            {
                hwnd = wih.Handle,
                dwFlags = FLASHW_ALL | FLASHW_TIMERNOFG,
                uCount = uint.MaxValue,
                dwTimeout = 0
            };

            fwi.cbSize = Convert.ToUInt32(Marshal.SizeOf(fwi));
            FlashWindowEx(ref fwi);
        }

        //Disgustang xml reader/writer

        public void ReadSettingsFile()
        {
            if (!File.Exists("Settings.xml"))
            {
                WriteSettingsFile();

                return;
            }

            XmlReaderSettings settings = new();

            try
            {
                using (XmlReader reader = XmlReader.Create("Settings.xml", settings))
                {

                    while (reader.Read())
                    {
                        switch (reader.NodeType)
                        {
                            case XmlNodeType.Element:
                                switch (reader.Name.ToLower())
                                {
                                    case "settings":
                                        while (reader.Read())
                                        {
                                            if (reader.NodeType == XmlNodeType.Element)
                                            {
                                                switch (reader.Name.ToLower())
                                                {
                                                    case "value":
                                                        ReadPropertyFromXml(reader);
                                                        break;
                                                }
                                            }
                                            else if (reader.NodeType == XmlNodeType.EndElement)
                                            {
                                                if (reader.Name.ToLower() == "settings")
                                                {
                                                    break;
                                                }
                                            }
                                        }

                                        break;
                                }
                                break;
                        }
                    }
                }

                if (!((bool)CheckBox_RememberPaths.IsChecked!))
                {
                    TextBox_Input.Text = "";
                    TextBox_Output.Text = "";
                }
            }
            catch (Exception ex)
            {
               MessageBox.Show(ex.Message, "Error reading settings file!");

                if (File.Exists("Settings.xml"))
                {
                    File.Delete("Settings.xml");
                }

                WriteSettingsFile();

                return;
            }
        }

        public void WriteSettingsFile()
        {
            XElement MainElement = new("Settings", new XAttribute("Date", DateTime.Now.ToString("G")));

            XElement Element = new("Value", new XAttribute("Name", "SeparateGlossMap"), new XAttribute("Type", typeof(bool).Name));
            Element.Value = ((bool)CheckBox_SeparateGlossMap.IsChecked!).ToString();

            MainElement.Add(Element);

            Element = new("Value", new XAttribute("Name", "SaveRawDDS"), new XAttribute("Type", typeof(bool).Name));
            Element.Value = ((bool)CheckBox_SaveRawDDS.IsChecked!).ToString();

            MainElement.Add(Element);

            Element = new("Value", new XAttribute("Name", "RememberPaths"), new XAttribute("Type", typeof(bool).Name));
            Element.Value = ((bool)CheckBox_RememberPaths.IsChecked!).ToString();

            MainElement.Add(Element);

            Element = new("Value", new XAttribute("Name", "DeleteSourceFiles"), new XAttribute("Type", typeof(bool).Name));
            Element.Value = ((bool)CheckBox_DeleteSourceFiles.IsChecked!).ToString();

            MainElement.Add(Element);

            Element = new("Value", new XAttribute("Name", "Recursive"), new XAttribute("Type", typeof(bool).Name));
            Element.Value = ((bool)CheckBox_Recursive.IsChecked!).ToString();

            MainElement.Add(Element);

            Element = new("Value", new XAttribute("Name", "InputPath"), new XAttribute("Type", typeof(string).Name));
            Element.Value = TextBox_Input.Text;

            MainElement.Add(Element);

            Element = new("Value", new XAttribute("Name", "OutputPath"), new XAttribute("Type", typeof(string).Name));
            Element.Value = TextBox_Output.Text;

            MainElement.Add(Element);

            XmlWriterSettings settings = new();
            settings.Indent = true;
            settings.IndentChars = "    ";
            settings.Encoding = Encoding.Unicode;

            using (XmlWriter writer = XmlWriter.Create("Settings.xml", settings))
            {
                MainElement.Save(writer);
            }
        }

        private void ReadPropertyFromXml(XmlReader reader)
        {
            string name = reader.GetAttribute("Name")!;
            string type = reader.GetAttribute("Type")!;
            reader.Read();

            switch (type.ToLower())
            {
                case "string":
                    switch (name)
                    {
                        case "InputPath":
                            TextBox_Input.Text = reader.Value;
                            break;

                        case "OutputPath":
                            TextBox_Output.Text = reader.Value;
                            break;
                    }
                    break;

                case "boolean":
                    switch (name)
                    {
                        case "SeparateGlossMap":
                            CheckBox_SeparateGlossMap.IsChecked = XmlConvert.ToBoolean(reader.Value.ToLower());
                            break;

                        case "SaveRawDDS":
                            CheckBox_SaveRawDDS.IsChecked = XmlConvert.ToBoolean(reader.Value.ToLower());
                            break;

                        case "RememberPaths":
                            CheckBox_RememberPaths.IsChecked = XmlConvert.ToBoolean(reader.Value.ToLower());
                            break;

                        case "DeleteSourceFiles":
                            CheckBox_DeleteSourceFiles.IsChecked = XmlConvert.ToBoolean(reader.Value.ToLower());
                            break;

                        case "Recursive":
                            CheckBox_Recursive.IsChecked = XmlConvert.ToBoolean(reader.Value.ToLower());
                            break;
                    }
                    break;
            }
        }
    }
}
﻿using System.IO;
using DirectXTexNet;

namespace KCDTextureExporter.DDS
{
    public class PixelFormat
    {
        public int Size { get; set; } = 32; //Always 32
        public int Flags { get; set; }
        public int FourCC { get; set; }
        public int RGBBitCount { get; set; }
        public int RBitMask { get; set; }
        public int GBitMask { get; set; }
        public int BBitMask { get; set; }
        public int ABitMask { get; set; }
        public PixelFormat()
        {

        }

        public PixelFormat(BinaryReader br)
        {
            Read(br);
        }

        public void Read(BinaryReader br)
        {
            int size = br.ReadInt32();

            if (size != Size)
            {
                throw new Exception("Error reading DDS PixelFormat.");
            }

            Flags = br.ReadInt32();
            FourCC = br.ReadInt32();
            RGBBitCount = br.ReadInt32();
            RBitMask = br.ReadInt32();
            GBitMask = br.ReadInt32();
            BBitMask = br.ReadInt32();
            ABitMask = br.ReadInt32();
        }

        public void Write(BinaryWriter bw)
        {
            bw.Write(Size);
            bw.Write(Flags);
            bw.Write(FourCC);
            bw.Write(RGBBitCount);
            bw.Write(RBitMask);
            bw.Write(GBitMask);
            bw.Write(BBitMask);
            bw.Write(ABitMask);
        }

        public DXGI_FORMAT GetPixelFormat()
        {
            switch (FourCC)
            {
                case 0x31545844:
                    return DXGI_FORMAT.BC1_UNORM;

                case 0x32545844:
                case 0x33545844:
                    return DXGI_FORMAT.BC2_UNORM;

                case 0x34545844:
                case 0x35545844:
                    return DXGI_FORMAT.BC3_UNORM;
            }

            return DXGI_FORMAT.UNKNOWN;
        }
    }
}

﻿using DirectXTexNet;
using System.IO;

namespace KCDTextureExporter.DDS
{
    public class Header
    {
        public int Size { get; set; } = 124; //Always 124
        public int Flags { get; set; }
        public int Height { get; set; }
        public int Width { get; set; }
        public int PitchOrLinearSize { get; set; }
        public int Depth { get; set; }
        public int MipMapCount { get; set; }
        public int[] Reserved1 { get; set; } = new int[11];
        public PixelFormat ddspf { get; set; } = new();
        public int Caps { get; set; }
        public int Caps2 { get; set; }
        public int Caps3 { get; set; }
        public int Caps4 { get; set; }
        public int Reserved2 { get; set; }
        public ExtendedHeader extendedHeader { get; set; } = new();
        public Header()
        {

        }

        public Header(BinaryReader br)
        {
            Read(br);
        }

        public void Read(BinaryReader br)
        {
            int size = br.ReadInt32();

            if (size != Size)
            {
                throw new Exception("Error reading DDS Header.");
            }

            Flags = br.ReadInt32();
            Height = br.ReadInt32();
            Width = br.ReadInt32();
            PitchOrLinearSize = br.ReadInt32();
            Depth = br.ReadInt32();
            MipMapCount = br.ReadInt32();

            for (int i = 0; i < Reserved1.Length; i++)
            {
                Reserved1[i] = br.ReadInt32();
            }

            ddspf = new(br);
            Caps = br.ReadInt32();
            Caps2 = br.ReadInt32();
            Caps3 = br.ReadInt32();
            Caps4 = br.ReadInt32();
            Reserved2 = br.ReadInt32();

            if (ddspf.FourCC == 0x30315844)
            {
                extendedHeader = new(br);
            }
        }

        public void Write(BinaryWriter bw)
        {
            bw.Write(Size);
            bw.Write(Flags);
            bw.Write(Height);
            bw.Write(Width);
            bw.Write(PitchOrLinearSize);
            bw.Write(Depth);
            bw.Write(MipMapCount);

            foreach (var val in Reserved1)
            {
                bw.Write(val);
            }

            ddspf.Write(bw);
            bw.Write(Caps);
            bw.Write(Caps2);
            bw.Write(Caps3);
            bw.Write(Caps4);
            bw.Write(Reserved2);

            if (ddspf.FourCC == 0x30315844)
            {
                extendedHeader.Write(bw);
            }
        }

        public DXGI_FORMAT GetPixelFormat()
        {
            if (ddspf.FourCC == 0x30315844)
            {
                return extendedHeader.dxgiFormat;
            }

            return ddspf.GetPixelFormat();
        }
    }
}

﻿using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Numerics;
using System.Runtime.InteropServices;
using DirectXTexNet;
using KCDTextureExporter.DDS;

namespace KCDTextureExporter
{
    public static class ImageConverter
    {
        public static void ConvertImage(
            string filePath,
            bool saveRawDDS,
            bool separateGlossMap,
            string outputPath = "",
            bool deleteSourceFiles = false,
            bool isOutputFolder = false)
        {
            // 1) Detect ID-map by suffix
            bool isIDMap = Path.GetFileNameWithoutExtension(filePath)
                               .EndsWith("_id", StringComparison.OrdinalIgnoreCase);

            // 2) Load DDS + optional alpha, collect temp mip filenames
            var (image, alpha, mipFiles, alphaMipFiles)
                = LoadGameDDS(filePath, saveRawDDS, deleteSourceFiles, outputPath, isOutputFolder);
            if (image == null)
                throw new InvalidOperationException("Failed to load DDS image.");

            // 3) Figure out formats
            var fmt = image.GetImage(0).Format;
            bool isNormal = fmt == DXGI_FORMAT.BC5_SNORM || fmt == DXGI_FORMAT.BC5_UNORM;
            bool isSRGB = TexHelper.Instance.IsSRGB(fmt);

            // 4) Decompress or convert to float RGBA for processing
            ScratchImage work = TexHelper.Instance.IsCompressed(fmt)
                ? image.Decompress(0, DXGI_FORMAT.R32G32B32A32_FLOAT)
                : (fmt != DXGI_FORMAT.R32G32B32A32_FLOAT
                    ? image.Convert(DXGI_FORMAT.R32G32B32A32_FLOAT, TEX_FILTER_FLAGS.DEFAULT, 0.5f)
                    : image);

            // 5) If normal map, reconstruct Z channel
            if (isNormal)
            {
                byte[] rec = ReconstructZ(GetPixelData(work), true);
                Marshal.Copy(rec, 0, work.GetImage(0).Pixels, rec.Length);
            }

            // 6) Handle alpha/gloss: split or merge
            if (alpha != null)
            {
                if (separateGlossMap)
                {
                    // extract gloss into its own TIFF
                    ScratchImage aImg = alpha;
                    var aFmt = aImg.GetImage(0, 0, 0).Format;
                    if (TexHelper.Instance.IsCompressed(aFmt))
                        aImg = aImg.Decompress(0, DXGI_FORMAT.R8_UNORM);
                    else if (aFmt != DXGI_FORMAT.R8_UNORM)
                        aImg = aImg.Convert(0, DXGI_FORMAT.R8_UNORM, TEX_FILTER_FLAGS.DEFAULT, 0.5f);

                    string dir = isOutputFolder
                        ? (string.IsNullOrEmpty(outputPath) ? Path.GetDirectoryName(filePath)! : outputPath)
                        : throw new Exception("Output must be a folder to separate gloss.");
                    string alphaPath = Path.Combine(dir, Path.GetFileNameWithoutExtension(filePath) + "_alpha.tif");
                    aImg.SaveToWICFile(0, WIC_FLAGS.NONE, TexHelper.Instance.GetWICCodec(WICCodecs.TIFF), alphaPath);
                    aImg.Dispose();
                }
                else
                {
                    // merge alpha back into float RGBA
                    ScratchImage aImg = alpha;
                    var aFmt = aImg.GetImage(0, 0, 0).Format;
                    if (TexHelper.Instance.IsCompressed(aFmt))
                        aImg = aImg.Decompress(0, DXGI_FORMAT.R32_FLOAT);
                    else if (aFmt != DXGI_FORMAT.R32_FLOAT)
                        aImg = aImg.Convert(0, DXGI_FORMAT.R32_FLOAT, TEX_FILTER_FLAGS.DEFAULT, 0.5f);

                    byte[] merged = MergeAlpha(GetPixelData(work), GetPixelData(aImg));
                    Marshal.Copy(merged, 0, work.GetImage(0).Pixels, merged.Length);
                    aImg.Dispose();
                }
                alpha.Dispose();
            }

            if (!isNormal)
            {
                if (isIDMap)
                {
                    byte[] q = QuantizeIDPixels(GetPixelData(work), isSRGB);
                    work = work.Convert(DXGI_FORMAT.R8G8B8A8_UNORM, TEX_FILTER_FLAGS.DEFAULT, 0.0f);
                    Marshal.Copy(q, 0, work.GetImage(0).Pixels, q.Length);
                }
                else
                {
                    work = work.Convert(
                        isSRGB ? DXGI_FORMAT.R8G8B8A8_UNORM_SRGB : DXGI_FORMAT.R8G8B8A8_UNORM,
                        TEX_FILTER_FLAGS.DEFAULT,
                        0.5f);
                }
            }

            // 8) Save final TIFF
            if (isOutputFolder)
            {
                string dir = string.IsNullOrEmpty(outputPath) ? Path.GetDirectoryName(filePath)! : outputPath;
                work.SaveToWICFile(0, WIC_FLAGS.NONE,
                    TexHelper.Instance.GetWICCodec(WICCodecs.TIFF),
                    Path.Combine(dir, Path.GetFileNameWithoutExtension(filePath) + ".tif"));
            }
            else
            {
                if (string.IsNullOrEmpty(outputPath))
                    throw new Exception("Incorrect output path.");
                work.SaveToWICFile(0, WIC_FLAGS.NONE,
                    TexHelper.Instance.GetWICCodec(WICCodecs.TIFF),
                    outputPath);
            }

            // 9) Cleanup
            image.Dispose();
            work.Dispose();

            if (deleteSourceFiles)
            {
                foreach (var f in mipFiles) if (File.Exists(f)) File.Delete(f);
                foreach (var f in alphaMipFiles) if (File.Exists(f)) File.Delete(f);
                if (!(saveRawDDS && isOutputFolder && string.IsNullOrEmpty(outputPath)))
                {
                    if (File.Exists(filePath)) File.Delete(filePath);
                    if (File.Exists(filePath + ".a")) File.Delete(filePath + ".a");
                }
            }
        }

        public static (ScratchImage? image, ScratchImage? alpha, List<string> mipFiles, List<string> alphaMipFiles)
        LoadGameDDS(string ddsFilePath,
                     bool saveRawDDS = false,
                     bool deleteSourceFiles = false,
                     string outputPath = "",
                     bool isOutputFolder = false)
        {
            ScratchImage? image = null;
            ScratchImage? alpha = null;
            var mipFiles = new List<string>();
            var alphaMipFiles = new List<string>();
            var mips = new List<byte[]>();
            var alphaMips = new List<byte[]>();

            // collect color mips
            for (int i = 1; i < 64; i++)
            {
                var p = ddsFilePath + "." + i;
                if (!File.Exists(p)) break;
                mips.Insert(0, File.ReadAllBytes(p));
                mipFiles.Add(p);
            }

            // collect alpha mips
            for (int i = 1; i < 64; i++)
            {
                var p = ddsFilePath + "." + i + "a";
                if (!File.Exists(p)) break;
                alphaMips.Insert(0, File.ReadAllBytes(p));
                alphaMipFiles.Add(p);
            }

            // read DDSFile (color)
            var ddsFile = new DDSFile(ddsFilePath, false);
            DDSFile? alphaDDS = File.Exists(ddsFilePath + ".a")
                               ? new DDSFile(ddsFilePath + ".a", true)
                               : null;

            // merge color mips + main DDS
            using (var ms = new MemoryStream())
            using (var bw = new BinaryWriter(ms))
            {
                foreach (var b in mips) bw.Write(b);
                bw.Write(ddsFile.Data!);
                ddsFile.Data = ms.ToArray();
            }

            int expectedSize = ComputePixelDataSize(
            ddsFile.Header.GetPixelFormat(),
            ddsFile.Header.Width,
            ddsFile.Header.Height,
            ddsFile.Header.MipMapCount);
                    if (ddsFile.Data.Length < expectedSize)
                        throw new Exception("Failed to load all necessary MIP levels.");

            if (saveRawDDS)
            {
                string tgt = isOutputFolder
                    ? Path.Combine(string.IsNullOrEmpty(outputPath) ? Path.GetDirectoryName(ddsFilePath)! : outputPath,
                                  Path.GetFileNameWithoutExtension(ddsFilePath) + ".dds")
                    : outputPath;
                if (string.IsNullOrEmpty(tgt)) throw new Exception("Incorrect output path.");
                ddsFile.Write(tgt);
            }

            // read alpha DDS if present
            if (alphaDDS != null)
            {
                using (var ms = new MemoryStream())
                using (var bw = new BinaryWriter(ms))
                {
                    foreach (var b in alphaMips) bw.Write(b);
                    bw.Write(alphaDDS.Data!);
                    alphaDDS.Data = ms.ToArray();
                }

                int alphaExpectedSize = ComputePixelDataSize(
                alphaDDS.Header.GetPixelFormat(),
                alphaDDS.Header.Width,
                alphaDDS.Header.Height,
                alphaDDS.Header.MipMapCount);
                    if (alphaDDS.Data.Length < alphaExpectedSize)
                        throw new Exception("Failed to load all necessary alpha MIP levels.");

                var data = alphaDDS.Write();
                var h = GCHandle.Alloc(data, GCHandleType.Pinned);
                try
                {
                    alpha = TexHelper.Instance.LoadFromDDSMemory(
                                h.AddrOfPinnedObject(),
                                data.Length,
                                DDS_FLAGS.ALLOW_LARGE_FILES);
                }
                finally { h.Free(); }
            }

            var imgData = ddsFile.Write();
            var h2 = GCHandle.Alloc(imgData, GCHandleType.Pinned);
            try
            {
                image = TexHelper.Instance.LoadFromDDSMemory(
                        h2.AddrOfPinnedObject(),
                        imgData.Length,
                        DDS_FLAGS.ALLOW_LARGE_FILES);
            }
            finally { h2.Free(); }

            return (image, alpha, mipFiles, alphaMipFiles);
        }

        public static byte[] GetPixelData(ScratchImage img)
        {
            int size = (int)img.GetPixelsSize();
            var buf = new byte[size];
            Marshal.Copy(img.GetPixels(), buf, 0, size);
            return buf;
        }

        public static byte[] ReconstructZ(byte[] pixelData, bool pack)
        {
            var vectors = new List<Vector2>();
            // read only when at least 16 bytes remain (4 X + 4 Y + 4 Z + 4 A)
            using (var ms = new MemoryStream(pixelData))
            using (var br = new BinaryReader(ms))
                while (ms.Position + 16 <= ms.Length)
                {
                    float x = br.ReadSingle();
                    float y = br.ReadSingle();
                    vectors.Add(new Vector2(x, y));
                    ms.Position += 8; // skip old Z and A
                }

            // write into a fresh, expandable buffer of the same size
            byte[] outData = new byte[pixelData.Length];
            using (var ms = new MemoryStream(outData))
            using (var bw = new BinaryWriter(ms))
                foreach (var v in vectors)
                {
                    float z = MathF.Sqrt(MathF.Max(0, 1 - Vector2.Dot(v, v)));
                    bw.Write(pack ? MathF.Pow((v.Y + 1) / 2, 2.2f) : v.Y);
                    bw.Write(pack ? MathF.Pow((v.X + 1) / 2, 2.2f) : v.X);
                    bw.Write(pack ? MathF.Pow((z + 1) / 2, 2.2f) : z);
                    bw.Write(1.0f);
                }

            return outData;
        }

        public static byte[] MergeAlpha(byte[] pixelData, byte[] alphaPixelData)
        {
            var colors = new List<Vector4>();
            using (var ms = new MemoryStream(pixelData))
            using (var br = new BinaryReader(ms))
                while (ms.Position < ms.Length)
                    colors.Add(new Vector4(
                        br.ReadSingle(),
                        br.ReadSingle(),
                        br.ReadSingle(),
                        br.ReadSingle()));

            var alphas = new List<float>();
            using (var ms = new MemoryStream(alphaPixelData))
            using (var br = new BinaryReader(ms))
                while (ms.Position < ms.Length)
                    alphas.Add(br.ReadSingle());

            using (var ms = new MemoryStream(pixelData))
            using (var bw = new BinaryWriter(ms))
            {
                for (int i = 0; i < colors.Count; i++)
                {
                    bw.Write(colors[i].X);
                    bw.Write(colors[i].Y);
                    bw.Write(colors[i].Z);
                    bw.Write(alphas[i]);
                }
                return ms.ToArray();
            }
        }

        public static byte[] QuantizeIDPixels(byte[] pixels, bool isSRGB)
        {
            using var inMs = new MemoryStream(pixels);
            using var br = new BinaryReader(inMs);
            using var outMs = new MemoryStream();
            using var bw = new BinaryWriter(outMs);

            while (inMs.Position < inMs.Length)
            {
                float r = br.ReadSingle();
                float g = br.ReadSingle();
                float b = br.ReadSingle();
                float a = br.ReadSingle();

                if (isSRGB)
                {
                    r = MathF.Pow(r, 1.0f / 2.2f);
                    g = MathF.Pow(g, 1.0f / 2.2f);
                    b = MathF.Pow(b, 1.0f / 2.2f);
                    bw.Write((byte)MathF.Ceiling(r * 255));
                    bw.Write((byte)MathF.Ceiling(g * 255));
                    bw.Write((byte)MathF.Ceiling(b * 255));
                    bw.Write((byte)MathF.Floor(a * 255));
                }
                else
                {
                    bw.Write((byte)MathF.Floor(r * 255));
                    bw.Write((byte)MathF.Floor(g * 255));
                    bw.Write((byte)MathF.Floor(b * 255));
                    bw.Write((byte)MathF.Floor(a * 255));
                }
            }
            return outMs.ToArray();
        }

        private static int ComputePixelDataSize(DXGI_FORMAT fmt, int w, int h, int mipCount)
        {
            int bits = TexHelper.Instance.BitsPerPixel(fmt);
            int size = w * h * bits;
            int total = size;
            for (int i = 1; i < mipCount; i++)
            {
                size /= 4;
                total += size;
            }
            return total / 8;
        }
    }
}
