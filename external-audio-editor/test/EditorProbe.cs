using System.IO;
using System.Text;

// A disposable test editor: records its argv beside the synthetic sample.
class EditorProbe {
    static int Main(string[] args) {
        if (args.Length != 1) return 2;
        File.WriteAllText(args[0] + ".received", args.Length + "\n" + args[0], new UTF8Encoding(false));
        return 0;
    }
}
