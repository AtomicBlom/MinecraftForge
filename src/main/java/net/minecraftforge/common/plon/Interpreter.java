package net.minecraftforge.common.plon;

import com.google.common.collect.ImmutableMap;

import static net.minecraftforge.common.plon.AST.*;

/**
 * Created by rainwarrior on 5/18/16.
 */
public class Interpreter
{
    public ISExp eval(ISExp exp, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv)
    {
        return eval(exp, new Cons(new Map(PrimOp.values), new Cons(new Map(topEnv), Nil.INSTANCE)));
    }

    public ISExp eval(ISExp exp)
    {
        return eval(exp, new Cons(new Map(PrimOp.values), Nil.INSTANCE));
    }

    private static final ISExp labelSymbol = makeSymbol("&labeled");
    private static final ISExp functionSymbol = makeSymbol("&function");
    static final ISExp mapSymbol = makeSymbol("&map");

    static int length(ISExp exp)
    {
        if (exp == Nil.INSTANCE || exp == MNil.INSTANCE)
        {
            return 0;
        }
        if (exp instanceof Cons)
        {
            return 1 + length(((Cons) exp).cdr);
        }
        if (exp instanceof Map)
        {
            return ((Map) exp).value.size();
        }
        throw new IllegalArgumentException("Length called neither on a list nor on a map");
    }

    private static IList bind(IList argNames, IList args, IList env)
    {
        if (length(argNames) != length(args))
        {
            throw new IllegalArgumentException("called bind with lists of different length");
        }
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        while (argNames != Nil.INSTANCE)
        {
            builder.put(((Cons) argNames).car, ((Cons) args).car);
            argNames = ((Cons) argNames).cdr;
            args = ((Cons) args).cdr;
        }
        ImmutableMap<ISExp, ISExp> frame = builder.build();
        IMap map;
        if (frame.isEmpty())
        {
            map = MNil.INSTANCE;
        }
        else
        {
            map = new Map(frame);
        }
        return new Cons(map, env);

    }

    private static ISExp makeFunction(IList args, ISExp body, IList env)
    {
        return new Cons(functionSymbol, new Cons(args, new Cons(body, new Cons(env, Nil.INSTANCE))));
    }

    private static void addLabel(ImmutableMap.Builder<Symbol, ISExp> frame, ISExp key, ISExp value)
    {
        if(key instanceof Symbol)
        {
            Symbol name = (Symbol) key;
            frame.put(name, new Cons(labelSymbol, new Cons(value, Nil.INSTANCE)));
            return;
        }
        else if(key instanceof Cons && ((Cons) key).car instanceof Symbol)
        {
            Cons cons = (Cons) key;
            Symbol name = (Symbol) cons.car;
            IList args = cons.cdr;
            frame.put(name, new Cons(labelSymbol, new Cons(args, new Cons(value, Nil.INSTANCE))));
            return;
        }
        throw new IllegalArgumentException("can't label: " + key);
    }

    private static ISExp unlabel(ISExp value, IList env)
    {
        if(value instanceof Cons && ((Cons) value).car.equals(labelSymbol))
        {
            if(length(value) == 3)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                Cons c3 = (Cons) c2.cdr;
                ISExp args = c2.car;
                ISExp body = c3.car;
                // function label
                if(args instanceof IList)
                {
                    return makeFunction((IList) args, body, env);
                }
            }
            else if (length(value) == 2)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                // reference label
                return eval(c2.car, env);
                /*if(c2.car instanceof Symbol)
                {
                    return lookup(env, (Symbol) c2.car);
                }*/
            }
        }
        return value;
    }

    private static ISExp lookup(IList env, Symbol name)
    {
        if (env == Nil.INSTANCE)
        {
            //return Unbound.INSTANCE;
            throw new IllegalStateException("Undefined variable " + name);
        }
        Cons cons = (Cons) env;
        if (cons.car == MNil.INSTANCE)
        {
            return lookup(cons.cdr, name);
        }
        if (cons.car instanceof Map)
        {
            Map map = (Map) cons.car;
            if (map.value.containsKey(name))
            {
                ISExp value = map.value.get(name);
                return unlabel(value, env);
            }
            return lookup(cons.cdr, name);
        }
        throw new IllegalArgumentException("lookup called with a list that has something other than a map:" + cons.car);
    }

    private static ISExp delay(ISExp exp)
    {
        return new Cons(makeSymbol("lambda"), new Cons(Nil.INSTANCE, new Cons(exp, Nil.INSTANCE)));
    }

    private static ISExp beval(Cons args, IList env)
    {
        if(args.cdr == Nil.INSTANCE)
        {
            return eval(args.car, env);
        }
        ISExp definitions = args.car;
        IMap frame;
        if (definitions instanceof Map)
        {
            ImmutableMap.Builder<Symbol, ISExp> frameBuilder = ImmutableMap.builder();
            Map map = (Map) definitions;
            for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
            {
                addLabel(frameBuilder, entry.getKey(), entry.getValue());
            }
            frame = new Map(frameBuilder.build());
        }
        else
        {
            ISExp frameExp = eval(definitions, env);
            if(!(frameExp instanceof IMap))
            {
                throw new IllegalArgumentException("bind needs either a map (that will be processed specially) or something that'll evaluate to it, got: " + frameExp);
            }
            frame = (IMap) frameExp;
        }
        return beval((Cons) args.cdr, new Cons(frame, env));
    }

    @SuppressWarnings("StringEquality")
    private static ISExp eval(ISExp exp, IList env)
    {
        if (
            exp instanceof FloatAtom ||
            exp instanceof StringAtom ||
            exp == Nil.INSTANCE ||
            exp == MNil.INSTANCE
        ) {
            return exp;
        }
        else if (exp instanceof Symbol)
        {
            return lookup(env, (Symbol) exp);
        }
        else if (exp instanceof ArithmSymbol)
        {
            return new ArithmOp((ArithmSymbol) exp);
        }
        else if (exp instanceof Cons)
        {
            Cons cons = (Cons) exp;
            if (cons.car instanceof Symbol)
            {
                String name = ((Symbol) cons.car).value;
                if (name == "quote")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("quote needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return cdr.car;
                }
                else if (name == "lambda")
                {
                    if (length(cons.cdr) != 2)
                    {
                        throw new IllegalArgumentException("lambda needs 2 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (!(c2.car instanceof IList))
                    {
                        throw new IllegalArgumentException("lambda needs a list as a first argument, got: " + c2.car);
                    }
                    IList args = (IList) c2.car;
                    ISExp body = c3.car;
                    return makeFunction(args, body, env);
                }
                else if (name == "bind")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("bind needs at least 1 argument");
                    }
                    Cons args = (Cons) cons.cdr;
                    return beval(args, env);
                }
                // TODO: macro?
                else if(name == "delay")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay needs 1 argument, got: " + exp);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return eval(delay(cdr.car), env);
                }
                // TODO: macro?
                else if(name == "delay_values")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay_values needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    if (!(cdr.car instanceof IMap))
                    {
                        throw new IllegalArgumentException("delay_values needs a map as a first argument, got: " + cdr.car);
                    }
                    if(cdr.car == MNil.INSTANCE)
                    {
                        return MNil.INSTANCE;
                    }
                    Map map = (Map) cdr.car;
                    ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                    for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                    {
                        builder.put(entry.getKey(), delay(entry.getValue()));
                    }
                    return eval(new Map(builder.build()), env);
                }
            }
            ISExp func = eval(cons.car, env);
            IList args = evlis(cons.cdr, env);
            return apply(func, args);
        }
        else if(exp instanceof Map)
        {
            Map map = (Map) exp;
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
            {
                builder.put(eval(entry.getKey(), env), eval(entry.getValue(), env));
            }
            return new Map(builder.build());
        }
        throw new IllegalStateException("eval of " + exp);
    }

    private static ISExp apply(ISExp func, IList args)
    {
        if (func instanceof ICallableAtom)
        {
            return ((ICallableAtom) func).apply(args);
        }
        if (func instanceof Cons)
        {
            Cons c1 = (Cons) func;
            if (functionSymbol.equals(c1.car) && c1.cdr != Nil.INSTANCE)
            {
                Cons c2 = (Cons) c1.cdr;
                if (c2.car instanceof IList && c2.cdr != Nil.INSTANCE)
                {
                    IList argNames = (IList) c2.car;
                    Cons c3 = (Cons) c2.cdr;
                    ISExp body = c3.car;
                    if (c3.cdr != Nil.INSTANCE)
                    {
                        Cons c4 = (Cons) c3.cdr;
                        ISExp env = c4.car;
                        if (env instanceof IList && c4.cdr == Nil.INSTANCE)
                        {
                            return eval(body, bind(argNames, args, (IList) env));
                        }
                    }
                }
            }
        }
        if (func instanceof Map)
        {
            Map map = (Map) func;
            if(length(args) != 1)
            {
                throw new IllegalArgumentException("Can only apply map to 1 argument");
            }
            Cons c1 = (Cons) args;
            if(map.value.containsKey(c1.car))
            {
                return map.value.get(c1.car);
            }
            throw new IllegalStateException("Map " + map + " doesn't have key " + c1.car);
        }
        throw new IllegalArgumentException("Don't know how to apply: " + func);
    }

    private static IList evlis(IList list, IList env)
    {
        if (list == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) list;
        return new Cons(eval(cons.car, env), evlis(cons.cdr, env));
    }
}
