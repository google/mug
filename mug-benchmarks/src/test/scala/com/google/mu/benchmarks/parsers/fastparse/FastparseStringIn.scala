package com.google.mu.benchmarks.parsers.fastparse

import fastparse._, NoWhitespace._

object FastparseStringIn {
  // Compile-time optimized StringIn parsers
  val fooParser: P[_] => P[Unit] = { implicit ctx: P[_] => StringIn("foobar", "foofoo", "foobaz", "foo", "bar") }
  val broadParser: P[_] => P[Unit] = { implicit ctx: P[_] => StringIn("aaaa", "aaab", "aaac", "aaad", "aaae", "aaaf", "aaag", "aaah", "aaai", "aaaj", "aaak", "aaal", "aaam", "aaan", "aaao", "aaap", "aaaq", "aaar", "aaas", "aaat", "aaau", "aaav", "aaaw", "aaax", "aaay", "aaaz", "bbba", "bbbb", "bbbc", "bbbd", "bbbe", "bbbf", "bbbg", "bbbh", "bbbi", "bbbj", "bbbk", "bbbl", "bbbm", "bbbn", "bbbo", "bbbp", "bbbq", "bbbr", "bbbs", "bbbt", "bbbu", "bbbv", "bbbw", "bbbx", "bbby", "bbbz", "ccca", "cccb", "cccc", "cccd", "ccce", "cccf", "cccg", "ccch", "ccci", "cccj", "ccck", "cccl", "cccm", "cccn", "ccco", "cccp", "cccq", "cccr", "cccs", "ccct", "cccu", "cccv", "cccw", "cccx", "cccy", "cccz", "ddda", "dddb", "dddc", "dddd", "ddde", "dddf", "dddg", "dddh", "dddi", "dddj", "dddk", "dddl", "dddm", "dddn", "dddo", "dddp", "dddq", "dddr", "ddds", "dddt", "dddu", "dddv", "dddw", "dddx", "dddy", "dddz", "eeea", "eeeb", "eeec", "eeed", "eeee", "eeef", "eeeg", "eeeh", "eeei", "eeej", "eeek", "eeel", "eeem", "eeen", "eeeo", "eeep", "eeeq", "eeer", "eees", "eeet", "eeeu", "eeev", "eeew", "eeex", "eeey", "eeez", "fffa", "fffb", "fffc", "fffd", "fffe", "ffff", "fffg", "fffh", "fffi", "fffj", "fffk", "fffl", "fffm", "fffn", "fffo", "fffp", "fffq", "fffr", "fffs", "ffft", "fffu", "fffv", "fffw", "fffx", "fffy", "fffz", "ggga", "gggb", "gggc", "gggd", "ggge", "gggf", "gggg", "gggh", "gggi", "gggj", "gggk", "gggl", "gggm", "gggn", "gggo", "gggp", "gggq", "gggr", "gggs", "gggt", "gggu", "gggv", "gggw", "gggx", "gggy", "gggz", "hhha", "hhhb", "hhhc", "hhhd", "hhhe", "hhhf", "hhhg", "hhhh", "hhhi", "hhhj", "hhhk", "hhhl", "hhhm", "hhhn", "hhho", "hhhp", "hhhq", "hhhr", "hhhs", "hhht", "hhhu", "hhhv", "hhhw", "hhhx", "hhhy", "hhhz", "iiia", "iiib", "iiic", "iiid", "iiie", "iiif", "iiig", "iiih", "iiii", "iiij", "iiik", "iiil", "iiim", "iiin", "iiio", "iiip", "iiiq", "iiir", "iiis", "iiit", "iiiu", "iiiv", "iiiw", "iiix", "iiiy", "iiiz", "jjja", "jjjb", "jjjc", "jjjd", "jjje", "jjjf", "jjjg", "jjjh", "jjji", "jjjj", "jjjk", "jjjl", "jjjm", "jjjn", "jjjo", "jjjp", "jjjq", "jjjr", "jjjs", "jjjt", "jjju", "jjjv", "jjjw", "jjjx", "jjjy", "jjjz", "kkka", "kkkb", "kkkc", "kkkd", "kkke", "kkkf", "kkkg", "kkkh", "kkki", "kkkj", "kkkk", "kkkl", "kkkm", "kkkn", "kkko", "kkkp", "kkkq", "kkkr", "kkks", "kkkt", "kkku", "kkkv", "kkkw", "kkkx", "kkky", "kkkz", "llla", "lllb", "lllc", "llld", "llle", "lllf", "lllg", "lllh", "llli", "lllj", "lllk", "llll", "lllm", "llln", "lllo", "lllp", "lllq", "lllr", "llls", "lllt", "lllu", "lllv", "lllw", "lllx", "llly", "lllz", "mmma", "mmmb", "mmmc", "mmmd", "mmme", "mmmf", "mmmg", "mmmh", "mmmi", "mmmj", "mmmk", "mmml", "mmmm", "mmmn", "mmmo", "mmmp", "mmmq", "mmmr", "mmms", "mmmt", "mmmu", "mmmv", "mmmw", "mmmx", "mmmy", "mmmz", "nnna", "nnnb", "nnnc", "nnnd", "nnne", "nnnf", "nnng", "nnnh", "nnni", "nnnj", "nnnk", "nnnl", "nnnm", "nnnn", "nnno", "nnnp", "nnnq", "nnnr", "nnns", "nnnt", "nnnu", "nnnv", "nnnw", "nnnx", "nnny", "nnnz", "oooa", "ooob", "oooc", "oood", "oooe", "ooof", "ooog", "oooh", "oooi", "oooj", "oook", "oool", "ooom", "ooon", "oooo", "ooop", "oooq", "ooor", "ooos", "ooot", "ooou", "ooov", "ooow", "ooox", "oooy", "oooz", "pppa", "pppb", "pppc", "pppd", "pppe", "pppf", "pppg", "ppph", "pppi", "pppj", "pppk", "pppl", "pppm", "pppn", "pppo", "pppp", "pppq", "pppr", "ppps", "pppt", "pppu", "pppv", "pppw", "pppx", "pppy", "pppz", "qqqa", "qqqb", "qqqc", "qqqd", "qqqe", "qqqf", "qqqg", "qqqh", "qqqi", "qqqj", "qqqk", "qqql", "qqqm", "qqqn", "qqqo", "qqqp", "qqqq", "qqqr", "qqqs", "qqqt", "qqqu", "qqqv", "qqqw", "qqqx", "qqqy", "qqqz", "rrra", "rrrb", "rrrc", "rrrd", "rrre", "rrrf", "rrrg", "rrrh", "rrri", "rrrj", "rrrk", "rrrl", "rrrm", "rrrn", "rrro", "rrrp", "rrrq", "rrrr", "rrrs", "rrrt", "rrru", "rrrv", "rrrw", "rrrx", "rrry", "rrrz", "sssa", "sssb", "sssc", "sssd", "ssse", "sssf", "sssg", "sssh", "sssi", "sssj", "sssk", "sssl", "sssm", "sssn", "ssso", "sssp", "sssq", "sssr", "ssss", "ssst", "sssu", "sssv", "sssw", "sssx", "sssy", "sssz", "ttta", "tttb", "tttc", "tttd", "ttte", "tttf", "tttg", "ttth", "ttti", "tttj", "tttk", "tttl", "tttm", "tttn", "ttto", "tttp", "tttq", "tttr", "ttts", "tttt", "tttu", "tttv", "tttw", "tttx", "ttty", "tttz", "uuua", "uuub", "uuuc", "uuud", "uuue", "uuuf", "uuug", "uuuh", "uuui", "uuuj", "uuuk", "uuul", "uuum", "uuun", "uuuo", "uuup", "uuuq", "uuur", "uuus", "uuut", "uuuu", "uuuv", "uuuw", "uuux", "uuuy", "uuuz", "vvva", "vvvb", "vvvc", "vvvd", "vvve", "vvvf", "vvvg", "vvvh", "vvvi", "vvvj", "vvvk", "vvvl", "vvvm", "vvvn", "vvvo", "vvvp", "vvvq", "vvvr", "vvvs", "vvvt", "vvvu", "vvvv", "vvvw", "vvvx", "vvvy", "vvvz", "wwwa", "wwwb", "wwwc", "wwwd", "wwwe", "wwwf", "wwwg", "wwwh", "wwwi", "wwwj", "wwwk", "wwwl", "wwwm", "wwwn", "wwwo", "wwwp", "wwwq", "wwwr", "wwws", "wwwt", "wwwu", "wwwv", "wwww", "wwwx", "wwwy", "wwwz", "xxxa", "xxxb", "xxxc", "xxxd", "xxxe", "xxxf", "xxxg", "xxxh", "xxxi", "xxxj", "xxxk", "xxxl", "xxxm", "xxxn", "xxxo", "xxxp", "xxxq", "xxxr", "xxxs", "xxxt", "xxxu", "xxxv", "xxxw", "xxxx", "xxxy", "xxxz", "yyya", "yyyb", "yyyc", "yyyd", "yyye", "yyyf", "yyyg", "yyyh", "yyyi", "yyyj", "yyyk", "yyyl", "yyym", "yyyn", "yyyo", "yyyp", "yyyq", "yyyr", "yyys", "yyyt", "yyyu", "yyyv", "yyyw", "yyyx", "yyyy", "yyyz", "zzza", "zzzb", "zzzc", "zzzd", "zzze", "zzzf", "zzzg", "zzzh", "zzzi", "zzzj", "zzzk", "zzzl", "zzzm", "zzzn", "zzzo", "zzzp", "zzzq", "zzzr", "zzzs", "zzzt", "zzzu", "zzzv", "zzzw", "zzzx", "zzzy", "zzzz") }

  // Dynamic oneOf parsers (using | operator)
  def makeOneOf(strings: Seq[String]): P[_] => P[Unit] = {
    val parsers: Seq[P[_] => P[Unit]] = strings.map(s => { implicit ctx: P[_] => s })
    parsers.reduce { (a, b) =>
      { implicit ctx: P[_] => a(ctx) | b(ctx) }
    }
  }
}

class FastparseStringInWrapper(test: String, strings: Seq[String]) {
  private val base = if (test == "foo") FastparseStringIn.fooParser else FastparseStringIn.broadParser
  private val parser: P[_] => P[Unit] = { implicit ctx: P[_] => base(ctx) ~ End }

  def parse(input: String): Boolean = {
    fastparse.parse(input, parser) match {
      case Parsed.Success(_, _) => true
      case _ => false
    }
  }
}

class FastparseOneOfWrapper(strings: Seq[String]) {
  private val base = FastparseStringIn.makeOneOf(strings)
  private val parser: P[_] => P[Unit] = { implicit ctx: P[_] => base(ctx) ~ End }

  def parse(input: String): Boolean = {
    fastparse.parse(input, parser) match {
      case Parsed.Success(_, _) => true
      case _ => false
    }
  }
}
