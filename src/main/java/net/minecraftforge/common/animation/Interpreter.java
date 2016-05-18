package net.minecraftforge.common.animation;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;

import static net.minecraftforge.common.animation.TimeValues.opsPattern;

/**
 * Created by rainwarrior on 5/18/16.
 */
public class Interpreter
{
    public static interface ISExp {}
    public static interface IAtom extends ISExp {}
    public static interface IStringAtom extends IAtom
    {
        String value();
    }
    public static interface ICallableAtom extends IAtom
    {
        ISExp apply(IList args);
    }

    public static final class FloatAtom implements IAtom
    {
        private final float value;

        public FloatAtom(float value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FloatAtom floatAtom = (FloatAtom) o;
            return Float.compare(floatAtom.value, value) == 0;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }

        @Override
        public String toString()
        {
            return Float.toString(value);
        }
    }

    public static final class StringAtom implements IStringAtom
    {
        private final String value;

        public StringAtom(String value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Symbol that = (Symbol) o;
            return Objects.equal(value, that.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }

        @Override
        public String toString()
        {
            return "\" " + value + "\"";
        }

        @Override
        public String value()
        {
            return " " + value;
        }
    }

    public static final class Symbol implements IStringAtom
    {
        private final String value;

        public Symbol(String value)
        {
            this.value = value.intern();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Symbol that = (Symbol) o;
            return value == that.value;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }

        @Override
        public String toString()
        {
            return "\"#" + value + "\"";
        }

        @Override
        public String value()
        {
            return "#" + value;
        }
    }

    private static int length(ISExp exp)
    {
        if(exp == Nil.INSTANCE || exp == MNil.INSTANCE)
        {
            return 0;
        }
        if(exp instanceof Cons)
        {
            return 1 + length(((Cons) exp).cdr);
        }
        if(exp instanceof Map)
        {
            return ((Map) exp).value.size();
        }
        throw new IllegalArgumentException("Length called neither on a list nor on a map");
    }

    public static class ArithmSymbol implements IStringAtom
    {
        private final String ops;

        public ArithmSymbol(String ops)
        {
            this.ops = ops;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArithmSymbol that = (ArithmSymbol) o;
            return Objects.equal(ops, that.ops);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(ops);
        }

        @Override
        public String toString()
        {
            return "\"" + ops + "\"";
        }

        @Override
        public String value()
        {
            return ops;
        }
    }

    public static class ArithmOp implements ICallableAtom
    {
        private final String ops;

        ArithmOp(String ops)
        {
            this.ops = ops;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArithmOp that = (ArithmOp) o;
            return Objects.equal(ops, that.ops);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(ops);
        }

        @Override
        public String toString()
        {
            return "\"" + ops + "\"";
        }

        @Override
        public FloatAtom apply(IList args)
        {
            if(length(args) != ops.length() + 1)
            {
                throw new IllegalArgumentException("arithmetic operator string \"" + ops + "\" needs " + ops.length() + " arguments, got " + args);
            }
            Cons cons = (Cons) args;
            if(!(cons.car instanceof FloatAtom))
            {
                throw new IllegalArgumentException("arithmetic operator needs a number, got " + cons.car);
            }
            float ret = ((FloatAtom) cons.car).value;
            for(int i = 0; i < ops.length(); i++)
            {
                cons = (Cons) cons.cdr;
                if(!(cons.car instanceof FloatAtom))
                {
                    throw new IllegalArgumentException("arithmetic operator needs a number, got " + cons.car);
                }
                float arg = ((FloatAtom) cons.car).value;
                switch(ops.charAt(i))
                {
                    case '+': ret += arg; break;
                    case '-': ret -= arg; break;
                    case '*': ret *= arg; break;
                    case '/': ret /= arg; break;
                    case 'm': ret = Math.min(ret, arg); break;
                    case 'M': ret = Math.max(ret, arg); break;
                    case 'r': ret = (float)Math.floor(ret / arg) * arg; break;
                    case 'R': ret = (float)Math.ceil(ret / arg) * arg; break;
                    case 'f': ret -= Math.floor(ret / arg) * arg; break;
                    case 'F': ret = (float)Math.ceil(ret / arg) * arg - ret; break;
                    default: throw new IllegalArgumentException("Unknown operator:" + ops.charAt(i));
                }
            }
            return new FloatAtom(ret);
        }
    }

    public enum PrimOp implements ICallableAtom
    {
        Length("length")
                {
                    @Override
                    public FloatAtom apply(IList args)
                    {
                        if(args == Nil.INSTANCE)
                        {
                            throw new IllegalArgumentException("Length with no arguments");
                        }
                        Cons cons = (Cons) args;
                        if(cons.cdr != Nil.INSTANCE)
                        {
                            throw new IllegalArgumentException("Length has too many arguments: " + cons);
                        }
                        return new FloatAtom(length(cons.car));
                    }
                },
        Cons("cons")
                {
                    @Override
                    public ISExp apply(IList args)
                    {
                        if(length(args) != 2)
                        {
                            throw new IllegalArgumentException("Cons needs 2 arguments, got: " + args);
                        }
                        Cons c1 = (Cons) args;
                        Cons c2 = (Cons) c1.cdr;
                        if(!(c2.car instanceof IList))
                        {
                            throw new IllegalArgumentException("Cons needs a list as a second argument");
                        }
                        return new Cons(c1.car, (IList) c2.car);
                    }
                },
        Car("car")
                {
                    @Override
                    public ISExp apply(IList args)
                    {
                        if(length(args) != 1)
                        {
                            throw new IllegalArgumentException("Car needs 1 argument, got: " + args);
                        }
                        Cons cons = (Cons) args;
                        if(cons.car instanceof Cons)
                        {
                            return ((Cons) cons.car).car;
                        }
                        throw new IllegalArgumentException("Car called not on a list");
                    }
                },
        Cdr("cdr")
                {
                    @Override
                    public ISExp apply(IList args)
                    {
                        if(length(args) != 1)
                        {
                            throw new IllegalArgumentException("Cdr needs 1 argument, got: " + args);
                        }
                        Cons cons = (Cons) args;
                        if(cons.car instanceof Cons)
                        {
                            return ((Cons) cons.car).cdr;
                        }
                        throw new IllegalArgumentException("Cdr called not on a list");
                    }
                },
        Map("map")
                {
                    @Override
                    public ISExp apply(IList args)
                    {
                        throw new NotImplementedException("map");
                    }
                },
        Conm("conm")
                {
                    @Override
                    public ISExp apply(IList args)
                    {
                        throw new NotImplementedException("conm");
                    }
                };

        private static final ImmutableMap<Symbol, PrimOp> values;
        static
        {
            ImmutableMap.Builder<Symbol, PrimOp> builder = ImmutableMap.builder();
            for(PrimOp op : values())
            {
                builder.put(op.name, op);
            }
            values = builder.build();
        }
        private final Symbol name;

        PrimOp(String name)
        {
            this.name = new Symbol(name);
        }

        @Override
        public String toString()
        {
            return "&" + name.value;
        }
    }

    public static class User implements ICallableAtom
    {
        private final String name;
        private final ITimeValue parameter;

        public User(String name, ITimeValue parameter)
        {
            this.name = name;
            this.parameter = parameter;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return Objects.equal(parameter, user.parameter);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(parameter);
        }

        @Override
        public String toString()
        {
            return "&user[" + name + "]";
        }

        public FloatAtom apply(IList args)
        {
            if(length(args) != 1)
            {
                throw new IllegalArgumentException("User parameter \"" + name + "\" needs 1 argument, got " + args);
            }
            Cons cons = (Cons) args;
            if(cons.car instanceof FloatAtom)
            {
                return new FloatAtom(parameter.apply(((FloatAtom) cons.car).value));
            }
            throw new IllegalArgumentException("User parameter \"" + name + "\" needs float argument, got " + cons.car);
        }
    }

    public static interface IList extends ISExp {}

    public static enum Nil implements IList
    {
        INSTANCE;

        @Override
        public String toString()
        {
            return "[]";
        }
    }

    public static final class Cons implements IList
    {
        private final ISExp car;
        private final IList cdr;

        public Cons(ISExp car, IList cdr)
        {
            this.car = car;
            this.cdr = cdr;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Cons cons = (Cons) o;
            return Objects.equal(car, cons.car) &&
                    Objects.equal(cdr, cons.cdr);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(car, cdr);
        }

        private String cdrString()
        {
            if(cdr == Nil.INSTANCE)
            {
                return "";
            }
            Cons cdr = (Cons)this.cdr;
            return ", " + cdr.car.toString() + cdr.cdrString();
        }

        @Override
        public String toString()
        {
            return "[" + car + cdrString() + "]";
        }
    }

    public static interface IMap extends ISExp {}

    public static enum MNil implements IMap
    {
        INSTANCE;
        @Override
        public String toString()
        {
            return "{}";
        }
    }

    public static final class Map implements IMap
    {
        private final ImmutableMap<? extends ISExp, ? extends ISExp> value;
        private final transient boolean isJsonifiable;

        public Map(ImmutableMap<? extends ISExp, ? extends ISExp> value)
        {
            this.value = value;
            boolean isJsonifiable = true;
            for(ISExp exp : value.keySet())
            {
                if(!(exp instanceof Symbol))
                {
                    isJsonifiable = false;
                    break;
                }
            }
            this.isJsonifiable = isJsonifiable;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Map map = (Map) o;
            return Objects.equal(value, map.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }

        @Override
        public String toString()
        {
            if(isJsonifiable)
            {
                return "{ " + Joiner.on(", ").withKeyValueSeparator(": ").join(value) + " }";
            }
            return "[\"#&map\", " + Joiner.on(", ").withKeyValueSeparator(", ").join(value) + "]";
        }
    }

    private static IList bind(IList argNames, IList args, IList env)
    {
        if(length(argNames) != length(args))
        {
            throw new IllegalArgumentException("called bind with lists of different length");
        }
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        while(argNames != Nil.INSTANCE)
        {
            builder.put(((Cons)argNames).car, ((Cons)args).car);
            argNames = ((Cons) argNames).cdr;
            args = ((Cons) args).cdr;
        }
        ImmutableMap<ISExp, ISExp> frame = builder.build();
        IMap map;
        if(frame.isEmpty())
        {
            map = MNil.INSTANCE;
        }
        else
        {
            map = new Map(frame);
        }
        return new Cons(map, env);

    }

    private static ISExp lookup(IList env, Symbol name)
    {
        if(env == Nil.INSTANCE)
        {
            return new Symbol("&unbound");
        }
        Cons cons = (Cons) env;
        if(cons.car == MNil.INSTANCE)
        {
            return lookup(cons.cdr, name);
        }
        if(cons.car instanceof Map)
        {
            Map map = (Map) cons.car;
            if(map.value.containsKey(name))
            {
                return map.value.get(name);
            }
            return lookup(cons.cdr, name);
        }
        throw new IllegalArgumentException("lookup called with a list that has something other than a map:" + cons.car);
    }

    public static ISExp eval(ISExp exp, Function<? super String, ? extends ITimeValue> userParameters)
    {
        return eval(exp, new Cons(new Map(PrimOp.values), Nil.INSTANCE), userParameters);
    }

    private static ISExp eval(ISExp exp, IList env, Function<? super String, ? extends ITimeValue> userParameters)
    {
        if(exp instanceof FloatAtom)
        {
            return exp;
        }
        if(exp instanceof StringAtom)
        {
            return exp;
        }
        else if(exp instanceof Symbol)
        {
            return lookup(env, (Symbol) exp);
        }
        else if(exp instanceof ArithmSymbol)
        {
            return new ArithmOp(((ArithmSymbol) exp).ops);
        }
        else if(exp instanceof Cons)
        {
            Cons cons = (Cons) exp;
            if(cons.car instanceof Symbol)
            {
                String name = ((Symbol) cons.car).value;
                if (name == "quote")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("quote with no arguments");
                    }
                    Cons cdr = (Cons) cons.cdr;
                    if (cdr.cdr != Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("quote with too many arguments: " + exp);
                    }
                    return cdr.car;
                }
                else if (name == "lambda")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("lambda with no arguments");
                    }
                    Cons c1 = (Cons) cons.cdr;
                    ISExp args = c1.car;
                    if (c1.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("lambda with 1 argument");
                    }
                    Cons c2 = (Cons) c1.cdr;
                    ISExp body = c2.car;
                    if (c2.cdr != Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("lambda with too many arguments: " + exp);
                    }
                    return new Cons(new Symbol("&function"), new Cons(args, new Cons(body, new Cons(env, Nil.INSTANCE))));
                }
                else if (name == "user")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("user needs 1 argument, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    if (c2.car instanceof StringAtom)
                    {
                        String parameterName = ((StringAtom) c2.car).value;
                        ITimeValue parameter = userParameters.apply(parameterName);
                        if (parameter == null)
                        {
                            return new Symbol("&unbound");
                        }
                        return new User(parameterName, parameter);
                    }
                    throw new IllegalArgumentException("user needs string argument, got: " + cons.car);
                }
            }
            return apply(eval(cons.car, env, userParameters), evlis(cons.cdr, env, userParameters), userParameters);
        }
        throw new IllegalStateException("eval of " + exp);
    }

    private static ISExp apply(ISExp func, IList args, Function<? super String, ? extends ITimeValue> userParameters)
    {
        if(func instanceof ICallableAtom)
        {
            return ((ICallableAtom)func).apply(args);
        }
        if(func instanceof Cons)
        {
            Cons c1 = (Cons) func;
            if(new Symbol("&function").equals(c1.car) && c1.cdr != Nil.INSTANCE)
            {
                Cons c2 = (Cons) c1.cdr;
                if(c2.car instanceof IList && c2.cdr != Nil.INSTANCE)
                {
                    IList argNames = (IList) c2.car;
                    Cons c3 = (Cons) c2.cdr;
                    ISExp body = c3.car;
                    if(c3.cdr != Nil.INSTANCE)
                    {
                        Cons c4 = (Cons) c3.cdr;
                        ISExp env = c4.car;
                        if(env instanceof IList && c4.cdr == Nil.INSTANCE)
                        {
                            return eval(body, bind(argNames, args, (IList) env), userParameters);
                        }
                    }
                }
            }
        }
        throw new IllegalArgumentException("Don't know how to apply: " + func);
    }

    private static IList evlis(IList list, IList env, Function<? super String, ? extends ITimeValue> userParameters)
    {
        if(list == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) list;
        return new Cons(eval(cons.car, env, userParameters), evlis(cons.cdr, env, userParameters));
    }

    public static enum SExpTypeAdapterFactory implements TypeAdapterFactory
    {
        INSTANCE;

        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
        {
            if (type.getRawType() != ISExp.class)
            {
                return null;
            }

            return (TypeAdapter<T>) new TypeAdapter<ISExp>()
            {
                public void write(JsonWriter out, ISExp parameter) throws IOException
                {
                    if(parameter instanceof FloatAtom)
                    {
                        out.value(((FloatAtom) parameter).value);
                    }
                    else if(parameter instanceof Symbol)
                    {
                        out.value(((Symbol) parameter).value);
                    }
                    else if(parameter == Nil.INSTANCE)
                    {
                        out.beginArray();
                        out.endArray();
                    }
                    else if(parameter instanceof Cons)
                    {
                        out.beginArray();
                        write(out, ((Cons) parameter).car);
                        for(IList cdr = ((Cons) parameter).cdr; cdr instanceof Cons; cdr = ((Cons) cdr).cdr)
                        {
                            write(out, ((Cons) cdr).car);
                        }
                        out.endArray();
                    }
                    else if(parameter == MNil.INSTANCE)
                    {
                        out.beginObject();
                        out.endObject();
                    }
                    else if(parameter instanceof Map)
                    {
                        // TODO
                    }
                }

                private IList readCdr(JsonReader in) throws IOException
                {
                    if(!in.hasNext())
                    {
                        return Nil.INSTANCE;
                    }
                    return new Cons(read(in), readCdr(in));
                }

                private ISExp parseString(String string)
                {
                    if(string.startsWith("#"))
                    {
                        return new Symbol(string.substring(1));
                    }
                    if(string.startsWith(" "))
                    {
                        return new StringAtom(string.substring(1));
                    }
                    if(opsPattern.matcher(string).matches())
                    {
                        return new ArithmOp(string);
                    }
                    throw new JsonParseException("Unknown string: \"" + string + "\"");
                }

                public ISExp read(JsonReader in) throws IOException
                {
                    switch(in.peek())
                    {
                        case STRING:
                            return parseString(in.nextString());
                        case NAME:
                            return parseString(in.nextName());
                        case NUMBER:
                            return new FloatAtom((float)in.nextDouble());
                        case BEGIN_ARRAY:
                            in.beginArray();
                            IList list = readCdr(in);
                            in.endArray();
                            if(list instanceof Cons)
                            {
                                Cons cons = (Cons) list;
                                if (cons.car.equals(new Symbol("&map")))
                                {
                                    // de-sugaring the map with non-string keys
                                    ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                                    list = ((Cons) list).cdr;
                                    while (list != Nil.INSTANCE)
                                    {
                                        Cons c1 = (Cons) list;
                                        Cons c2 = (Cons) c1.cdr;
                                        builder.put(c1.car, c2.car);
                                        list = c2.cdr;
                                    }
                                    ImmutableMap<ISExp, ISExp> value = builder.build();
                                    IMap map;
                                    if (value.isEmpty())
                                    {
                                        map = MNil.INSTANCE;
                                    } else
                                    {
                                        map = new Map(value);
                                    }
                                    return map;
                                }
                            }
                            return list;
                        case BEGIN_OBJECT:
                            in.beginObject();
                            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                            while(in.hasNext())
                            {
                                ISExp key = read(in);
                                ISExp value = read(in);
                                builder.put(key, value);
                            }
                            in.endObject();
                            return new Map(builder.build());
                        default:
                            throw new JsonParseException("Unexpected item in the bagging area: \"" + in.peek() + "\"");
                    }
                }
            };
        }
    }
}
